package com.distributedsearch.router.controller;

import com.distributedsearch.router.model.SearchResult;
import com.distributedsearch.router.service.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for handling search requests.
 * Provides the /search endpoint that clients use to query the distributed search system.
 */
@RestController
public class RouterController {

    private static final Logger logger = LoggerFactory.getLogger(RouterController.class);

    @Autowired
    private QueryService queryService;

    /**
     * Search endpoint.
     * 
     * GET /search?q=helix
     * 
     * Accepts a query parameter and returns search results from all shard nodes.
     * 
     * @param query The search query string
     * @return List of search results in JSON format
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> search(@RequestParam("q") String query) {
        logger.info("Search request received: q={}", query);
        
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Empty query received");
            return ResponseEntity.badRequest().build();
        }
        
        try {
            // Execute distributed search
            List<SearchResult> results = queryService.search(query.trim());
            
            logger.info("Search completed: {} results", results.size());
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            logger.error("Search failed for query '{}': {}", query, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check endpoint.
     * 
     * GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Query Router is running");
    }
}
