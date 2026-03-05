package com.distributedsearch.helixcontroller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelixConfig {
    private static final Logger logger = LoggerFactory.getLogger(HelixConfig.class);
    
    // Cluster settings from environment or defaults
    public static final String CLUSTER_NAME = System.getenv().getOrDefault("CLUSTER_NAME", "search-cluster");
    public static final String RESOURCE_NAME = "search-index";
    public static final String STATE_MODEL = "MasterSlave";
    public static final String ZK_ADDRESS = System.getenv().getOrDefault("ZOOKEEPER_ADDRESS", "zookeeper:2181");

    // Shard and replication settings with safe parsing
    public static final int NUM_SHARDS = parseIntSafely("NUM_SHARDS", "2");
    public static final int NUM_REPLICAS = parseIntSafely("NUM_REPLICAS", "2");

    // Participant instance names — must match the NODE_ID env var used by search-node containers
    public static final String[] PARTICIPANT_NODES = {
            "search-node-1",
            "search-node-2",
            "search-node-3",
            "search-node-4"
    };
    
    /**
     * Safely parse integer from environment variable with fallback
     */
    private static int parseIntSafely(String envVarName, String defaultValue) {
        String value = System.getenv().getOrDefault(envVarName, defaultValue);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                logger.warn("Invalid value for {}: {}. Must be >= 1. Using default: {}", 
                           envVarName, value, defaultValue);
                return Integer.parseInt(defaultValue);
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.error("Invalid numeric value for {}: '{}'. Using default: {}", 
                        envVarName, value, defaultValue);
            return Integer.parseInt(defaultValue);
        }
    }
}
