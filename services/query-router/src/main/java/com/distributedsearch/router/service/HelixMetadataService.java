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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Apache Helix cluster.
 * Retrieves cluster metadata to discover shard leaders.
 */
@Service
public class HelixMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(HelixMetadataService.class);

    @Value("${helix.cluster.name:search-cluster}")
    private String clusterName;

    @Value("${helix.zookeeper.address:zookeeper:2181}")
    private String zkAddress;

    private HelixManager helixManager;

    @PostConstruct
    public void init() {
        try {
            // Wait for cluster to be ready
            logger.info("Waiting 10 seconds for cluster to initialize...");
            Thread.sleep(10000);
            
            // Connect to Helix cluster as a SPECTATOR (read-only observer)
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
            // For development: continue without Helix (will use fallback node list)
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
     * Get list of leader node addresses for all shards.
     * Each shard has one leader that handles queries for that partition.
     * 
     * @return List of node addresses (e.g., ["search-node-1:8080", "search-node-2:8080"])
     */
    public List<String> getShardLeaders() {
        List<String> leaders = new ArrayList<>();
        
        try {
            if (helixManager != null && helixManager.isConnected()) {
                // Get external view of the search index resource using HelixDataAccessor
                PropertyKey.Builder keyBuilder = helixManager.getHelixDataAccessor().keyBuilder();
                ExternalView externalView = helixManager.getHelixDataAccessor()
                    .getProperty(keyBuilder.externalView("search-index"));
                
                if (externalView != null) {
                    // Iterate through partitions to find MASTER instances
                    for (String partition : externalView.getPartitionSet()) {
                        Map<String, String> stateMap = externalView.getStateMap(partition);
                        
                        for (Map.Entry<String, String> entry : stateMap.entrySet()) {
                            if ("MASTER".equals(entry.getValue())) {
                                String instance = entry.getKey();
                                // Convert instance name to network address
                                String address = convertInstanceToAddress(instance);
                                leaders.add(address);
                                logger.debug("Found leader for {}: {}", partition, address);
                            }
                        }
                    }
                    
                    logger.info("Retrieved {} shard leaders from Helix", leaders.size());
                    return leaders;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to retrieve shard leaders from Helix: {}", e.getMessage());
        }
        
        // Fallback: return static list of nodes if Helix is unavailable
        logger.info("Using fallback node list");
        return getFallbackNodes();
    }

    /**
     * Fallback node list when Helix is not available.
     * Assumes nodes are reachable via these hostnames.
     */
    private List<String> getFallbackNodes() {
        return List.of(
            "search-node-1:8080",
            "search-node-2:8080",
            "search-node-3:8080",
            "search-node-4:8080"
        );
    }

    /**
     * Convert Helix instance name to network address.
     * Instance names are just hostnames in our setup (e.g., "search-node-1")
     * We append the port 8080 since all search nodes use the same port.
     */
    private String convertInstanceToAddress(String instance) {
        // Instance names are like "search-node-1", add port
        return instance + ":8080";
    }
}
