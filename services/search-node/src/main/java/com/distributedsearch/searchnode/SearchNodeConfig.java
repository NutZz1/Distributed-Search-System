package com.distributedsearch.searchnode;

public class SearchNodeConfig {
    public static final String CLUSTER_NAME = System.getenv().getOrDefault("CLUSTER_NAME", "search-cluster");
    public static final String STATE_MODEL = "MasterSlave";
    public static final String ZK_ADDRESS = System.getenv().getOrDefault("ZOOKEEPER_ADDRESS", "zookeeper:2181");
}
