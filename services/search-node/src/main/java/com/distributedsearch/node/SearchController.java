package com.distributedsearch.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * REST controller for search node operations.
 * Handles document indexing and search queries.
 */
@RestController
public class SearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private SearchService service;

    /**
     * Index a document.
     * Only succeeds if this node is MASTER for the document's shard.
     */
    @PostMapping("/index")
    public ResponseEntity<String> indexDocument(@RequestBody Document doc) {
        try {
            // Validate input
            if (doc == null || doc.getId() < 0) {
                logger.warn("Invalid document received: {}", doc);
                return ResponseEntity.badRequest().body("Invalid document: ID must be >= 0");
            }
            
            if (doc.getTitle() == null || doc.getTitle().trim().isEmpty()) {
                logger.warn("Document missing title: {}", doc.getId());
                return ResponseEntity.badRequest().body("Invalid document: title is required");
            }

            service.addDocument(doc);
            logger.info("Successfully indexed document: {}", doc.getId());
            return ResponseEntity.ok("Document indexed successfully");
            
        } catch (IllegalStateException e) {
            // Not responsible for this shard or not MASTER
            logger.warn("Cannot index document: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                 .body("Error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error indexing document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Replication endpoint — called by the MASTER to push a document copy to this SLAVE.
     * Bypasses partition-ownership and MASTER checks; the document is stored directly.
     */
    @PostMapping("/replicate")
    public ResponseEntity<String> replicateDocument(@RequestBody Document doc) {
        try {
            if (doc == null || doc.getId() < 0) {
                return ResponseEntity.badRequest().body("Invalid document");
            }
            service.storeDocumentDirectly(doc);
            logger.info("[Replication] Accepted replicated document: {}", doc.getId());
            return ResponseEntity.ok("Replicated");
        } catch (Exception e) {
            logger.error("[Replication] Error storing replicated document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Replication error: " + e.getMessage());
        }
    }

    /**
     * Search for documents matching the query.
     * Returns results only from partitions assigned to this node.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam String q) {
        try {
            if (q == null || q.trim().isEmpty()) {
                logger.warn("Empty search query received");
                return ResponseEntity.badRequest().build();
            }
            
            logger.info("Search query received: '{}'", q);
            List<Document> results = service.search(q);
            logger.info("Returning {} results for query: '{}'", results.size(), q);
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("Error processing search query: '{}'", q, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Snapshot endpoint — called by a rejoining SLAVE to pull all documents
     * for a given partition from this MASTER and catch up on missed writes.
     *
     * GET /snapshot?partition=search-index_0
     */
    @GetMapping("/snapshot")
    public ResponseEntity<List<Document>> snapshot(@RequestParam String partition) {
        try {
            if (partition == null || partition.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            List<Document> docs = service.getDocumentsForPartition(partition);
            logger.info("[Snapshot] Serving {} documents for partition: {}", docs.size(), partition);
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            logger.error("[Snapshot] Error serving snapshot for partition: {}", partition, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    /**
     * Status endpoint - shows which partitions this node is serving
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            Map<String, Boolean> partitions = service.getAssignedPartitions();
            return ResponseEntity.ok(Map.of(
                "status", "running",
                "assigned_partitions", partitions,
                "num_partitions", partitions.size()
            ));
        } catch (Exception e) {
            logger.error("Error getting status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}