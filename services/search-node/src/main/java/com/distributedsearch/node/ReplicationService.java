package com.distributedsearch.node;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.PropertyKey;
import org.apache.helix.model.ExternalView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles replication of indexed documents from MASTER to SLAVE nodes.
 *
 * When a MASTER node indexes a document it calls replicateToSlaves(),
 * which reads the Helix ExternalView to discover which nodes are currently
 * SLAVE for that partition and pushes the document to each of them via
 * POST /replicate.
 *
 * Replication is best-effort: if a SLAVE is unreachable the error is logged
 * but the write still succeeds on the MASTER.
 */
@Service
public class ReplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);
    private static final String RESOURCE_NAME = "search-index";

    private final String instanceName = System.getenv().getOrDefault("NODE_ID", "search-node-1");
    private final String zkAddress    = System.getenv().getOrDefault("ZOOKEEPER_ADDRESS", "zookeeper:2181");
    private final String clusterName  = System.getenv().getOrDefault("CLUSTER_NAME", "search-cluster");

    private HelixManager spectatorManager;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Connect to the Helix cluster as a SPECTATOR so we can read ExternalView
     * without participating in leader election.
     *
     * The connection is attempted once at startup. If it fails (e.g. cluster not
     * ready yet) replication will silently skip — the SLAVE can still be brought
     * up-to-date via a future re-index or a background sync.
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("[Replication] Connecting to Helix as SPECTATOR (node={})", instanceName);
            spectatorManager = HelixManagerFactory.getZKHelixManager(
                    clusterName,
                    instanceName + "-repl-spectator",
                    InstanceType.SPECTATOR,
                    zkAddress
            );
            spectatorManager.connect();
            logger.info("[Replication] Connected to Helix cluster: {}", clusterName);
        } catch (Exception e) {
            logger.warn("[Replication] Could not connect to Helix ({}). Replication disabled until restart.",
                    e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (spectatorManager != null && spectatorManager.isConnected()) {
            spectatorManager.disconnect();
            logger.info("[Replication] Disconnected spectator from Helix");
        }
    }

    /**
     * Push a document to every SLAVE that currently owns {@code partitionName}.
     *
     * @param doc           the document that was just indexed on the MASTER
     * @param partitionName e.g. "search-index_0"
     */
    public void replicateToSlaves(Document doc, String partitionName) {
        List<String> slaves = getSlavesForPartition(partitionName);

        if (slaves.isEmpty()) {
            logger.debug("[Replication] No slaves found for partition: {}", partitionName);
            return;
        }

        logger.info("[Replication] Replicating document {} to {} slave(s) for partition {}",
                doc.getId(), slaves.size(), partitionName);

        for (String slave : slaves) {
            pushToNode(slave, doc);
        }
    }

    /**
     * Catch-up sync: called when this node transitions OFFLINE → SLAVE.
     * Finds the current MASTER for {@code partitionName}, fetches its full
     * document snapshot via GET /snapshot, and bulk-loads all documents locally.
     *
     * This ensures a rejoining node is consistent before it starts serving reads.
     */
    public void syncFromMaster(String partitionName, SearchService searchService) {
        String master = getMasterForPartition(partitionName);
        if (master == null) {
            logger.warn("[Sync] No MASTER found for partition: {}. Skipping catch-up.", partitionName);
            return;
        }

        logger.info("[Sync] Starting catch-up sync for partition: {} from MASTER: {}", partitionName, master);
        try {
            String url = "http://" + master + ":8080/snapshot?partition=" + partitionName;
            String response = restTemplate.getForObject(url, String.class);
            List<Document> docs = objectMapper.readValue(
                    response, new TypeReference<List<Document>>() {});

            for (Document doc : docs) {
                searchService.storeDocumentDirectly(doc);
            }
            logger.info("[Sync] ✓ Caught up {} documents for partition: {} from MASTER: {}",
                    docs.size(), partitionName, master);
        } catch (Exception e) {
            logger.error("[Sync] ✗ Failed to sync partition: {} from MASTER: {}: {}",
                    partitionName, master, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** Discover the current MASTER instance for a partition from Helix ExternalView. */
    private String getMasterForPartition(String partitionName) {
        try {
            if (spectatorManager != null && spectatorManager.isConnected()) {
                PropertyKey.Builder keyBuilder = spectatorManager.getHelixDataAccessor().keyBuilder();
                ExternalView externalView = spectatorManager.getHelixDataAccessor()
                        .getProperty(keyBuilder.externalView(RESOURCE_NAME));

                if (externalView != null) {
                    Map<String, String> stateMap = externalView.getStateMap(partitionName);
                    if (stateMap != null) {
                        for (Map.Entry<String, String> entry : stateMap.entrySet()) {
                            if ("MASTER".equals(entry.getValue())) {
                                return entry.getKey();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[Sync] Error reading ExternalView for partition {}: {}", partitionName, e.getMessage());
        }
        return null;
    }

    /** Discover the current SLAVE instances for a partition from Helix ExternalView. */
    private List<String> getSlavesForPartition(String partitionName) {
        List<String> slaves = new ArrayList<>();
        try {
            if (spectatorManager != null && spectatorManager.isConnected()) {
                PropertyKey.Builder keyBuilder = spectatorManager.getHelixDataAccessor().keyBuilder();
                ExternalView externalView = spectatorManager.getHelixDataAccessor()
                        .getProperty(keyBuilder.externalView(RESOURCE_NAME));

                if (externalView != null) {
                    Map<String, String> stateMap = externalView.getStateMap(partitionName);
                    if (stateMap != null) {
                        for (Map.Entry<String, String> entry : stateMap.entrySet()) {
                            if ("SLAVE".equals(entry.getValue())) {
                                slaves.add(entry.getKey());
                            }
                        }
                    }
                } else {
                    logger.warn("[Replication] ExternalView not available yet for resource: {}", RESOURCE_NAME);
                }
            } else {
                logger.warn("[Replication] Spectator not connected, skipping replication for partition: {}", partitionName);
            }
        } catch (Exception e) {
            logger.error("[Replication] Error reading ExternalView for partition {}: {}", partitionName, e.getMessage());
        }
        return slaves;
    }

    /** Send the document to a single node's /replicate endpoint. */
    private void pushToNode(String nodeInstanceName, Document doc) {
        try {
            String url = "http://" + nodeInstanceName + ":8080/replicate";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(doc);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
            logger.info("[Replication] ✓ Document {} replicated to slave: {}", doc.getId(), nodeInstanceName);
        } catch (Exception e) {
            logger.error("[Replication] ✗ Failed to replicate document {} to slave {}: {}",
                    doc.getId(), nodeInstanceName, e.getMessage());
        }
    }
}
