package com.distributedsearch.router.service;

import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.PropertyKey;
import org.apache.helix.model.ExternalView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

/**
 * Service for interacting with Apache Helix cluster.
 * Retrieves cluster metadata to discover shard leaders and full topology.
 */
@Service
public class HelixMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(HelixMetadataService.class);
    private static final String RESOURCE_NAME = "search-index";
    private static final int NUM_SHARDS = 2;

    // Static fallback map: shard -> ordered list of nodes to try (primary first)
    private static final Map<Integer, List<String>> SHARD_FALLBACKS = Map.of(
        0, List.of("search-node-1", "search-node-2"),
        1, List.of("search-node-3", "search-node-4")
    );

    @Value("${helix.cluster.name:search-cluster}")
    private String clusterName;

    @Value("${helix.zookeeper.address:zookeeper:2181}")
    private String zkAddress;

    private HelixManager helixManager;

    @PostConstruct
    public void init() {
        try {
            logger.info("Waiting 10 seconds for cluster to initialize...");
            Thread.sleep(10000);
            logger.info("Connecting to Helix cluster: {} via ZooKeeper: {}", clusterName, zkAddress);
            helixManager = HelixManagerFactory.getZKHelixManager(
                clusterName,
                "QueryRouter-" + System.currentTimeMillis(),
                InstanceType.SPECTATOR,
                zkAddress
            );
            helixManager.connect();
            logger.info("Successfully connected to Helix cluster");
        } catch (Exception e) {
            logger.error("Failed to connect to Helix cluster: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (helixManager != null && helixManager.isConnected()) {
            helixManager.disconnect();
            logger.info("Disconnected from Helix cluster");
        }
    }

    /**
     * Get list of MASTER node hostnames for all shards (used by QueryService).
     * Returns hostname only (e.g. "search-node-1:8080"), not full URL.
     */
    public List<String> getShardLeaders() {
        List<String> leaders = new ArrayList<>();
        try {
            ExternalView externalView = getExternalView();
            if (externalView != null) {
                for (String partition : externalView.getPartitionSet()) {
                    Map<String, String> stateMap = externalView.getStateMap(partition);
                    for (Map.Entry<String, String> entry : stateMap.entrySet()) {
                        if ("MASTER".equals(entry.getValue())) {
                            leaders.add(entry.getKey() + ":8080");
                            logger.debug("Found leader for {}: {}", partition, entry.getKey());
                        }
                    }
                }
                logger.info("Retrieved {} shard leaders from Helix", leaders.size());
                return leaders;
            }
        } catch (Exception e) {
            logger.warn("Failed to retrieve shard leaders from Helix: {}", e.getMessage());
        }
        logger.info("Using fallback node list");
        return getFallbackNodes();
    }

    /**
     * Get the full cluster topology:
     *   partition -> { instanceName -> role }
     *
     * Nodes not present in the ExternalView for a given partition are OFFLINE.
     */
    public Map<String, Map<String, String>> getClusterTopology() {
        Map<String, Map<String, String>> topology = new LinkedHashMap<>();

        // Pre-populate all shards so the UI always shows them even before nodes join
        for (int i = 0; i < NUM_SHARDS; i++) {
            topology.put(RESOURCE_NAME + "_" + i, new LinkedHashMap<>());
        }

        try {
            ExternalView externalView = getExternalView();
            if (externalView != null) {
                for (String partition : new TreeSet<>(externalView.getPartitionSet())) {
                    Map<String, String> stateMap = externalView.getStateMap(partition);
                    if (stateMap != null) {
                        topology.put(partition, new LinkedHashMap<>(stateMap));
                    }
                }
                logger.debug("Cluster topology: {} partitions", topology.size());
            } else {
                logger.warn("ExternalView not available — topology will show no roles");
            }
        } catch (Exception e) {
            logger.error("Failed to get cluster topology: {}", e.getMessage());
        }
        return topology;
    }

    /**
     * Find which node is currently MASTER for a given shard id (0-based).
     * Returns the raw instance name e.g. "search-node-1" (no port).
     * Returns null if Helix is unavailable or no MASTER is elected yet.
     */
    public String getMasterForShard(int shardId) {
        String partition = RESOURCE_NAME + "_" + shardId;
        try {
            ExternalView externalView = getExternalView();
            if (externalView != null) {
                Map<String, String> stateMap = externalView.getStateMap(partition);
                if (stateMap != null) {
                    for (Map.Entry<String, String> entry : stateMap.entrySet()) {
                        if ("MASTER".equals(entry.getValue())) {
                            logger.debug("MASTER for shard {}: {}", shardId, entry.getKey());
                            return entry.getKey();
                        }
                    }
                    logger.warn("No MASTER elected yet for partition: {}", partition);
                } else {
                    logger.warn("Partition {} not in ExternalView yet", partition);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find MASTER for shard {}: {}", shardId, e.getMessage());
        }
        return null;   // caller decides fallback
    }

    /**
     * Get all nodes that are either MASTER or SLAVE for a shard.
     * Used by the router to build a retry list when the MASTER is unknown.
     */
    public List<String> getNodesForShard(int shardId) {
        String partition = RESOURCE_NAME + "_" + shardId;
        List<String> nodes = new ArrayList<>();
        try {
            ExternalView externalView = getExternalView();
            if (externalView != null) {
                Map<String, String> stateMap = externalView.getStateMap(partition);
                if (stateMap != null) {
                    // Return MASTER first, then SLAVEs
                    stateMap.entrySet().stream()
                        .sorted((a, b) -> "MASTER".equals(a.getValue()) ? -1 : 1)
                        .map(Map.Entry::getKey)
                        .forEach(nodes::add);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get nodes for shard {}: {}", shardId, e.getMessage());
        }
        if (nodes.isEmpty()) {
            // Fallback order for each shard
            nodes.addAll(SHARD_FALLBACKS.getOrDefault(shardId, List.of()));
        }
        return nodes;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private ExternalView getExternalView() {
        if (helixManager != null && helixManager.isConnected()) {
            PropertyKey.Builder keyBuilder = helixManager.getHelixDataAccessor().keyBuilder();
            return helixManager.getHelixDataAccessor()
                .getProperty(keyBuilder.externalView(RESOURCE_NAME));
        }
        return null;
    }

    private List<String> getFallbackNodes() {
        return List.of(
            "search-node-1:8080",
            "search-node-2:8080",
            "search-node-3:8080",
            "search-node-4:8080"
        );
    }
}
