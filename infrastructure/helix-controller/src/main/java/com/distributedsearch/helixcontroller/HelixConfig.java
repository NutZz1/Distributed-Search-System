package com.distributedsearch.helixcontroller;

public class HelixConfig {
    // Cluster settings from environment or defaults
    public static final String CLUSTER_NAME = System.getenv().getOrDefault("CLUSTER_NAME", "search-cluster");
    public static final String RESOURCE_NAME = "search-index";
    public static final String STATE_MODEL = "MasterSlave";
    public static final String ZK_ADDRESS = System.getenv().getOrDefault("ZOOKEEPER_ADDRESS", "zookeeper:2181");

    // Shard and replication settings
    public static final int NUM_SHARDS = Integer.parseInt(System.getenv().getOrDefault("NUM_SHARDS", "2"));
    public static final int NUM_REPLICAS = Integer.parseInt(System.getenv().getOrDefault("NUM_REPLICAS", "2"));

    // Participant instance names — must match the NODE_ID env var used by search-node containers
    public static final String[] PARTICIPANT_NODES = {
            "search-node-1",
            "search-node-2",
            "search-node-3",
            "search-node-4"
    };
}
