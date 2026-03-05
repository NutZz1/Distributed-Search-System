package com.distributedsearch.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class SearchNodeMain {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchNodeMain.class);

    public static void main(String[] args) throws Exception {

        String instanceName = System.getenv().getOrDefault("NODE_ID", "search-node-1");
        String zkAddress = System.getenv().getOrDefault("ZOOKEEPER_ADDRESS", "zookeeper:2181");
        String clusterName = System.getenv().getOrDefault("CLUSTER_NAME", "search-cluster");

        logger.info("==============================================");
        logger.info("   Distributed Search Node: {}", instanceName);
        logger.info("==============================================");
        logger.info("Cluster   : {}", clusterName);
        logger.info("ZooKeeper : {}", zkAddress);

        // Start Spring Boot to initialize SearchService bean
        logger.info("Starting Spring Boot application...");
        ConfigurableApplicationContext context = SpringApplication.run(SearchNodeMain.class, args);
        
        // Get the SearchService and ReplicationService beans from Spring context
        SearchService searchService = context.getBean(SearchService.class);
        ReplicationService replicationService = context.getBean(ReplicationService.class);
        logger.info("✓ SearchService and ReplicationService beans initialized");

        // Connect to Helix cluster as PARTICIPANT
        logger.info("Connecting to Helix cluster as PARTICIPANT...");
        HelixManager manager = HelixManagerFactory.getZKHelixManager(
            clusterName,
            instanceName,
            InstanceType.PARTICIPANT,
            zkAddress
        );

        // Register state model factory for handling partition assignments
        logger.info("Registering MasterSlave state model factory...");
        manager.getStateMachineEngine().registerStateModelFactory(
            "MasterSlave",
            new SearchStateModelFactory(searchService, replicationService)
        );

        // Connect to cluster
        manager.connect();
        logger.info("==============================================");
        logger.info("   ✓ Connected to Helix cluster");
        logger.info("==============================================");
        logger.info("Node '{}' is now waiting for partition assignments...", instanceName);
        logger.info("Helix will assign shards dynamically.");

        // Add shutdown hook for graceful disconnect
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down search node: {}", instanceName);
            if (manager.isConnected()) {
                manager.disconnect();
                logger.info("Disconnected from Helix cluster");
            }
        }));
    }
}