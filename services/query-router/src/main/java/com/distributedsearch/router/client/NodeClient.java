package com.distributedsearch.router.client;

import com.distributedsearch.router.model.Document;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for communicating with individual search nodes.
 * Sends search queries to nodes and parses their responses.
 */
@Component
public class NodeClient {

    private static final Logger logger = LoggerFactory.getLogger(NodeClient.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NodeClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a search query to a specific node and return results.
     * 
     * @param nodeAddress The address of the search node (e.g., "search-node-1:8080")
     * @param query The search query string
     * @return List of search results from this node
     */
    public List<Document> search(String nodeAddress, String query) {
        try {
            String encodedQuery = UriUtils.encode(query, StandardCharsets.UTF_8);
            String url = String.format("http://%s/search?q=%s", nodeAddress, encodedQuery);
            logger.info("Sending query '{}' to node: {}", query, nodeAddress);
            
            // Send GET request to search node
            String response = restTemplate.getForObject(url, String.class);
            
            // Parse JSON response into list of Document objects
            List<Document> results = objectMapper.readValue(
                response, 
                new TypeReference<List<Document>>() {}
            );
            
            logger.info("Received {} results from node: {}", results.size(), nodeAddress);
            return results;
            
        } catch (Exception e) {
            logger.error("Failed to query node {}: {}", nodeAddress, e.getMessage());
            // Return empty list on failure
            return new ArrayList<>();
        }
    }
}
