package com.distributedsearch.helixcontroller;

import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.model.StateModelDefinition;
import org.apache.helix.tools.StateModelConfigGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClusterSetup {
    private static final Logger logger = LoggerFactory.getLogger(ClusterSetup.class);

    public static void setup() {
        String zkAddress = HelixConfig.ZK_ADDRESS;
        String clusterName = HelixConfig.CLUSTER_NAME;

        logger.info("=== Starting Cluster Setup ===");
        logger.info("ZooKeeper Address : {}", zkAddress);
        logger.info("Cluster Name      : {}", clusterName);

        ZKHelixAdmin admin = new ZKHelixAdmin(zkAddress);

        try {
            // Step 1: Create the cluster if it doesn't exist
            if (admin.addCluster(clusterName, false)) {
                logger.info("[CLUSTER] Created new cluster: {}", clusterName);
            } else {
                logger.info("[CLUSTER] Cluster already exists: {}", clusterName);
            }

            // Step 2: Add MasterSlave state model definition (skip if it already exists)
            List<String> existingModels = admin.getStateModelDefs(clusterName);
            if (!existingModels.contains(HelixConfig.STATE_MODEL)) {
                admin.addStateModelDef(clusterName, HelixConfig.STATE_MODEL,
                        new StateModelDefinition(
                                new StateModelConfigGenerator().generateConfigForMasterSlave()));
                logger.info("[STATE MODEL] Added state model: {}", HelixConfig.STATE_MODEL);
            } else {
                logger.info("[STATE MODEL] State model already exists: {}", HelixConfig.STATE_MODEL);
            }

            // Step 3: Register each search node as a participant instance
            for (String nodeName : HelixConfig.PARTICIPANT_NODES) {
                List<String> existingInstances = admin.getInstancesInCluster(clusterName);
                if (!existingInstances.contains(nodeName)) {
                    InstanceConfig instanceConfig = new InstanceConfig(nodeName);
                    instanceConfig.setHostName(nodeName);
                    instanceConfig.setPort("8080");
                    instanceConfig.setInstanceEnabled(true);
                    admin.addInstance(clusterName, instanceConfig);
                    logger.info("[PARTICIPANT] Registered node: {}", nodeName);
                } else {
                    logger.info("[PARTICIPANT] Node already registered: {}", nodeName);
                }
            }

            // Step 4: Add resource with sharding (skip if already exists)
            List<String> existingResources = admin.getResourcesInCluster(clusterName);
            if (!existingResources.contains(HelixConfig.RESOURCE_NAME)) {
                int numShards = HelixConfig.NUM_SHARDS;
                int numReplicas = HelixConfig.NUM_REPLICAS;

                admin.addResource(clusterName, HelixConfig.RESOURCE_NAME,
                        numShards, HelixConfig.STATE_MODEL, "FULL_AUTO");
                logger.info("[RESOURCE] Added resource: {} with {} shards", HelixConfig.RESOURCE_NAME, numShards);

                // Step 5: Trigger rebalance so Helix assigns shards to nodes
                admin.rebalance(clusterName, HelixConfig.RESOURCE_NAME, numReplicas);
                logger.info("[REBALANCE] Rebalanced resource: {} with {} replicas", HelixConfig.RESOURCE_NAME, numReplicas);
            } else {
                logger.info("[RESOURCE] Resource already exists: {}", HelixConfig.RESOURCE_NAME);
            }

            // Step 6: Print summary
            logger.info("=== Cluster Setup Complete ===");
            logger.info("Cluster     : {}", clusterName);
            logger.info("State Model : {}", HelixConfig.STATE_MODEL);
            logger.info("Participants: {}", String.join(", ", HelixConfig.PARTICIPANT_NODES));
            logger.info("Resource    : {} ({} shards, {} replicas)",
                    HelixConfig.RESOURCE_NAME, HelixConfig.NUM_SHARDS, HelixConfig.NUM_REPLICAS);
            logger.info("Helix will automatically assign:");
            logger.info("  Shard_0 -> Node1 (MASTER), Node2 (SLAVE)");
            logger.info("  Shard_1 -> Node3 (MASTER), Node4 (SLAVE)");
            logger.info("Actual assignments depend on which nodes connect.");

        } catch (Exception e) {
            logger.error("Error during cluster setup", e);
            throw new RuntimeException("Cluster setup failed", e);
        } finally {
            admin.close();
        }
    }
}
