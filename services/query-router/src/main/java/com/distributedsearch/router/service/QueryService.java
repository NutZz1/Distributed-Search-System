package com.distributedsearch.router.service;

import com.distributedsearch.router.client.NodeClient;
import com.distributedsearch.router.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Core query routing service.
 * Distributes search queries to all shard leaders and aggregates results.
 */
@Service
public class QueryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 1;
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    @Autowired
    private HelixMetadataService helixMetadataService;

    @Autowired
    private NodeClient nodeClient;

    private final ExecutorService executorService;

    public QueryService() {
        // Thread pool for parallel queries to multiple nodes
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Execute a search query across all shard leaders.
     * Queries are sent in parallel for low latency.
     * Results from all nodes are merged and deduplicated.
     * 
     * @param query The search query string
     * @return Merged list of search results from all nodes
     */
    public List<Document> search(String query) {
        logger.info("Received search query: {}", query);
        
        // Get current shard leaders from Helix
        List<String> leaders = helixMetadataService.getShardLeaders();
        logger.info("Querying {} shard leaders", leaders.size());
        
        // Execute queries to all leaders in parallel
        List<Document> allResults = queryNodesInParallel(leaders, query, 0);
        
        // Deduplicate results by document ID
        Map<Integer, Document> uniqueResults = new LinkedHashMap<>();
        for (Document doc : allResults) {
            uniqueResults.putIfAbsent(doc.getId(), doc);
        }
        
        List<Document> finalResults = new ArrayList<>(uniqueResults.values());
        logger.info("Returning {} unique results from {} total for query: {}", 
                    finalResults.size(), allResults.size(), query);
        return finalResults;
    }

    /**
     * Query multiple nodes in parallel and merge results.
     * Implements retry logic for failed nodes.
     */
    private List<Document> queryNodesInParallel(List<String> nodes, String query, int attemptNumber) {
        List<Future<List<Document>>> futures = new ArrayList<>();
        
        // Submit query tasks for each node
        for (String node : nodes) {
            Future<List<Document>> future = executorService.submit(() -> {
                return nodeClient.search(node, query);
            });
            futures.add(future);
        }
        
        // Collect results from all nodes
        List<Document> mergedResults = new ArrayList<>();
        List<String> failedNodes = new ArrayList<>();
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                // Wait for node response with timeout
                List<Document> nodeResults = futures.get(i).get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                mergedResults.addAll(nodeResults);
                
            } catch (TimeoutException e) {
                String node = nodes.get(i);
                logger.error("Timeout querying node: {}", node);
                failedNodes.add(node);
                
            } catch (Exception e) {
                String node = nodes.get(i);
                logger.error("Error querying node {}: {}", node, e.getMessage());
                failedNodes.add(node);
            }
        }
        
        // Retry failed nodes if this is the first attempt
        if (!failedNodes.isEmpty() && attemptNumber < MAX_RETRY_ATTEMPTS) {
            logger.info("Retrying {} failed nodes", failedNodes.size());
            
            // Refresh leader list from Helix — may now contain newly elected MASTERs
            List<String> refreshedLeaders = helixMetadataService.getShardLeaders();
            
            // Nodes that already responded successfully this attempt
            List<String> successfulNodes = new ArrayList<>(nodes);
            successfulNodes.removeAll(failedNodes);
            
            // Retry any refreshed leader that we haven't already heard from.
            // This correctly picks up newly elected MASTERs for shards whose old
            // MASTER just failed — the dead node will NOT appear in refreshedLeaders,
            // but its replacement will.
            List<String> nodesToRetry = new ArrayList<>();
            for (String refreshedLeader : refreshedLeaders) {
                if (!successfulNodes.contains(refreshedLeader)) {
                    nodesToRetry.add(refreshedLeader);
                }
            }
            
            if (!nodesToRetry.isEmpty()) {
                logger.info("Retrying against new leaders: {}", nodesToRetry);
                List<Document> retryResults = queryNodesInParallel(nodesToRetry, query, attemptNumber + 1);
                mergedResults.addAll(retryResults);
            } else {
                logger.warn("No new leaders found for failed shards — results may be incomplete");
            }
        }
        
        return mergedResults;
    }

    /**
     * Shutdown executor service when service is destroyed
     */
    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Query service executor shut down");
    }
}
