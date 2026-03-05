package com.distributedsearch.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shard-aware search service.
 * Only stores and searches documents that belong to assigned partitions.
 */
@Service
public class SearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    
    // Thread-safe storage
    private final Map<Integer, Document> documents = new ConcurrentHashMap<>();
    private final InvertedIndex index = new InvertedIndex();
    
    // Track which partitions this node is responsible for
    private final Map<String, PartitionState> assignedPartitions = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int NUM_SHARDS = 2; // Should match Helix configuration
    private static final String RESOURCE_NAME = "search-index";

    /**
     * Represents the state of a partition assigned to this node
     */
    private static class PartitionState {
        boolean isMaster;
        
        PartitionState(boolean isMaster) {
            this.isMaster = isMaster;
        }
    }

    /**
     * Add a partition to this node's responsibility.
     * Called by Helix state transition handlers.
     */
    public void addPartition(String partitionName, boolean isMaster) {
        assignedPartitions.put(partitionName, new PartitionState(isMaster));
        logger.info("✓ Now serving partition: {} as {}", partitionName, isMaster ? "MASTER" : "SLAVE");
    }

    /**
     * Promote a partition from SLAVE to MASTER.
     * Called when Helix promotes this node.
     */
    public void promoteToMaster(String partitionName) {
        PartitionState state = assignedPartitions.get(partitionName);
        if (state != null) {
            state.isMaster = true;
            logger.info("✓ Promoted to MASTER for partition: {}", partitionName);
        }
    }

    /**
     * Demote a partition from MASTER to SLAVE.
     * Called during rebalancing.
     */
    public void demoteToSlave(String partitionName) {
        PartitionState state = assignedPartitions.get(partitionName);
        if (state != null) {
            state.isMaster = false;
            logger.info("✓ Demoted to SLAVE for partition: {}", partitionName);
        }
    }

    /**
     * Remove a partition from this node's responsibility.
     * Called when Helix reassigns the partition elsewhere.
     */
    public void removePartition(String partitionName) {
        assignedPartitions.remove(partitionName);
        logger.info("✓ No longer serving partition: {}", partitionName);
        
        // Optional: Clean up documents that belonged to this partition
        // For now, we keep them in case partition comes back
    }

    /**
     * Add a document to the index.
     * Uses hash-based partitioning to determine shard ownership.
     */
    public synchronized void addDocument(Document doc) {
        // Determine which partition owns this document
        int shardId = getShardForDocument(doc.getId());
        String partitionName = RESOURCE_NAME + "_" + shardId;
        
        // Check if we're responsible for this partition
        PartitionState state = assignedPartitions.get(partitionName);
        
        if (state == null) {
            logger.warn("❌ Rejecting document {}: Not responsible for partition {}", 
                       doc.getId(), partitionName);
            throw new IllegalStateException(
                "This node is not assigned to partition " + partitionName + 
                ". Document belongs to shard " + shardId);
        }
        
        if (!state.isMaster) {
            logger.warn("❌ Rejecting document {}: Not MASTER for partition {}", 
                       doc.getId(), partitionName);
            throw new IllegalStateException(
                "This node is SLAVE for partition " + partitionName + 
                ". Only MASTER nodes accept writes.");
        }
        
        // Store the document
        documents.put(doc.getId(), doc);
        index.addDocument(doc);
        
        logger.info("✓ Indexed document {} to partition {} (shard {})", 
                   doc.getId(), partitionName, shardId);
    }

    /**
     * Search for documents matching the query.
     * Only searches documents in assigned partitions.
     */
    public List<Document> search(String term) {
        logger.debug("Searching for: '{}' in {} assigned partitions", 
                    term, assignedPartitions.size());
        
        List<Integer> ids = index.search(term);
        List<Document> results = new ArrayList<>();

        for (Integer id : ids) {
            Document doc = documents.get(id);
            if (doc != null) {
                // Verify document still belongs to an assigned partition
                int shardId = getShardForDocument(id);
                String partitionName = RESOURCE_NAME + "_" + shardId;
                
                if (assignedPartitions.containsKey(partitionName)) {
                    results.add(doc);
                }
            }
        }

        logger.debug("Found {} results for query: '{}'", results.size(), term);
        return results;
    }

    /**
     * Determine which shard a document belongs to using consistent hashing.
     * Uses simple modulo hash: hash(docId) % numShards
     */
    private int getShardForDocument(int documentId) {
        // Simple hash-based partitioning
        return Math.abs(documentId % NUM_SHARDS);
    }

    /**
     * Get current partition assignments (for debugging/monitoring)
     */
    public Map<String, Boolean> getAssignedPartitions() {
        Map<String, Boolean> result = new HashMap<>();
        assignedPartitions.forEach((partition, state) -> 
            result.put(partition, state.isMaster)
        );
        return result;
    }
}