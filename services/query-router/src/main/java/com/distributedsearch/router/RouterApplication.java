package com.distributedsearch.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for the Query Router.
 * 
 * The Query Router is the entry point for search queries in the distributed search system.
 * It receives user queries, discovers shard leaders from Apache Helix, distributes queries
 * to the appropriate search nodes, and aggregates results.
 * 
 * Architecture:
 *   User -> Query Router -> Helix (metadata) -> Search Nodes -> Inverted Index -> Results
 * 
 * This application runs on port 8080 and provides:
 *   - GET /search?q=<query> - Execute distributed search
 *   - GET /health - Health check endpoint
 */
@SpringBootApplication
public class RouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouterApplication.class, args);
    }
}
