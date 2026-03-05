package com.distributedsearch.router.controller;

import com.distributedsearch.router.model.Document;
import com.distributedsearch.router.service.AdminService;
import com.distributedsearch.router.service.HelixMetadataService;
import com.distributedsearch.router.service.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the distributed search dashboard.
 *
 * Endpoints:
 *   GET  /search?q=<query>       — distributed search across all shard MASTERs
 *   POST /index                  — proxy document to the correct shard MASTER (with retry)
 *   GET  /cluster/topology       — full node → shard → role map from Helix
 *   POST /node/{nodeId}/stop     — stop a search-node Docker container
 *   POST /node/{nodeId}/start    — start a stopped search-node Docker container
 *   GET  /health                 — health check
 */
@RestController
@CrossOrigin(origins = "*")
public class RouterController {

    private static final Logger logger = LoggerFactory.getLogger(RouterController.class);
    private static final int NUM_SHARDS = 2;

    @Autowired private QueryService queryService;
    @Autowired private HelixMetadataService helixMetadataService;
    @Autowired private AdminService adminService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // Search
    // =========================================================================

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(
            @RequestParam(value = "q", defaultValue = "") String query) {
        logger.info("Search request: query={}", query);
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            List<Document> results = queryService.search(query.trim());
            logger.info("Search completed: {} results", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Search failed for '{}': {}", query, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // Index — proxy to the correct shard MASTER with retry
    // =========================================================================

    /**
     * Accepts a document from the frontend and forwards it to the search-node
     * that is currently MASTER for this document's shard.
     *
     * Retry logic:
     *   1. Ask Helix for the MASTER of the target shard.
     *   2. POST to that node.
     *   3. If the node returns 403 (not MASTER / wrong shard) or is unreachable,
     *      try the next candidate node for that shard.
     *   4. If all candidates fail, return 503.
     */
    @PostMapping("/index")
    public ResponseEntity<String> indexDocument(@RequestBody Document doc) {
        logger.info("Index request: docId={}", doc.getId());

        // Basic validation
        if (doc.getTitle() == null || doc.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Title is required");
        }

        int shardId = Math.abs(doc.getId() % NUM_SHARDS);
        logger.info("Document {} belongs to shard {}", doc.getId(), shardId);

        // Get ordered candidate list — MASTER from Helix first, then fallbacks
        List<String> candidates = buildCandidateList(shardId);
        logger.info("Trying candidates for shard {}: {}", shardId, candidates);

        String serializedDoc;
        try {
            serializedDoc = objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Serialization error: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(serializedDoc, headers);

        for (String node : candidates) {
            String url = "http://" + node + ":8080/index";
            logger.info("Trying to index doc {} at {}", doc.getId(), url);
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("✓ Document {} indexed successfully at {}", doc.getId(), node);
                    return ResponseEntity.ok(
                        "Indexed to node: " + node + " (shard " + shardId + ")");
                }
                logger.warn("Node {} returned {}: {}", node, response.getStatusCode(), response.getBody());
            } catch (HttpClientErrorException e) {
                // 403 = this node is not MASTER for this shard — try next
                logger.warn("Node {} rejected doc {} ({}), trying next candidate",
                            node, doc.getId(), e.getStatusCode());
            } catch (Exception e) {
                // Node unreachable or other error — try next
                logger.warn("Could not reach node {}: {}", node, e.getMessage());
            }
        }

        String msg = "No available MASTER found for shard " + shardId +
                     ". Tried: " + candidates + ". Is the cluster running?";
        logger.error(msg);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(msg);
    }

    /**
     * Build an ordered list of candidate nodes for a shard.
     * The Helix-reported MASTER goes first; fallback static nodes come after.
     */
    private List<String> buildCandidateList(int shardId) {
        // getNodesForShard already puts the MASTER first and includes fallbacks
        return helixMetadataService.getNodesForShard(shardId);
    }

    // =========================================================================
    // Cluster topology
    // =========================================================================

    @GetMapping("/cluster/topology")
    public ResponseEntity<Map<String, Map<String, String>>> clusterTopology() {
        try {
            Map<String, Map<String, String>> topology = helixMetadataService.getClusterTopology();
            return ResponseEntity.ok(topology);
        } catch (Exception e) {
            logger.error("Failed to get cluster topology: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // Node control (Docker socket)
    // =========================================================================

    @PostMapping("/node/{nodeId}/stop")
    public ResponseEntity<String> stopNode(@PathVariable String nodeId) {
        logger.info("Stop request for node: {}", nodeId);
        if (!isValidNodeId(nodeId)) {
            return ResponseEntity.badRequest().body("Invalid node ID: " + nodeId);
        }
        boolean ok = adminService.stopNode(nodeId);
        return ok
            ? ResponseEntity.ok("Node " + nodeId + " stopped successfully")
            : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to stop " + nodeId);
    }

    @PostMapping("/node/{nodeId}/start")
    public ResponseEntity<String> startNode(@PathVariable String nodeId) {
        logger.info("Start request for node: {}", nodeId);
        if (!isValidNodeId(nodeId)) {
            return ResponseEntity.badRequest().body("Invalid node ID: " + nodeId);
        }
        boolean ok = adminService.startNode(nodeId);
        return ok
            ? ResponseEntity.ok("Node " + nodeId + " started successfully")
            : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to start " + nodeId);
    }

    @GetMapping("/node/{nodeId}/status")
    public ResponseEntity<Map<String, String>> getNodeStatus(@PathVariable String nodeId) {
        if (!isValidNodeId(nodeId)) {
            return ResponseEntity.badRequest().build();
        }
        String status = adminService.getNodeStatus(nodeId);
        return ResponseEntity.ok(Map.of("status", status));
    }

    // =========================================================================
    // Health
    // =========================================================================

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Query Router is running");
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private boolean isValidNodeId(String nodeId) {
        return nodeId != null && nodeId.matches("search-node-[1-4]");
    }
}
