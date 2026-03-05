package com.distributedsearch.helixcontroller;

import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelixController {
    private static final Logger logger = LoggerFactory.getLogger(HelixController.class);

    public static void main(String[] args) {
        String zkAddress = HelixConfig.ZK_ADDRESS;
        String clusterName = HelixConfig.CLUSTER_NAME;

        System.out.println("==============================================");
        System.out.println("   Distributed Search — Helix Controller");
        System.out.println("==============================================");
        System.out.println("ZooKeeper : " + zkAddress);
        System.out.println("Cluster   : " + clusterName);

        logger.info("==============================================");
        logger.info("   Distributed Search — Helix Controller");
        logger.info("==============================================");
        logger.info("ZooKeeper : {}", zkAddress);
        logger.info("Cluster   : {}", clusterName);

        // Small delay to ensure ZooKeeper is fully ready
        try {
            logger.info("Waiting 5 seconds for ZooKeeper to be fully ready...");
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Step 1: Set up the cluster (create cluster, add state model, register nodes, add resource)
        logger.info("Initializing cluster setup...");
        ClusterSetup.setup();

        // Step 2: Start the Helix Controller
        logger.info("Starting Helix Controller for cluster: {}", clusterName);

        try {
            HelixManager manager = HelixManagerFactory.getZKHelixManager(
                    clusterName,
                    "controller_main",
                    InstanceType.CONTROLLER,
                    zkAddress);

            manager.connect();

            logger.info("==============================================");
            logger.info("   Helix Controller CONNECTED successfully!");
            logger.info("==============================================");
            logger.info("Controller is now managing cluster: {}", clusterName);
            logger.info("Waiting for search nodes to join...");
            logger.info("Leader election and shard assignment will happen automatically.");

            // Keep the controller running forever
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error starting Helix Controller", e);
            System.exit(1);
        }
    }
}
