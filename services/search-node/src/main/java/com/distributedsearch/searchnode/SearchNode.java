package com.distributedsearch.searchnode;

import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNode {
    private static final Logger logger = LoggerFactory.getLogger(SearchNode.class);

    public static void main(String[] args) {
        String nodeId = System.getenv().getOrDefault("NODE_ID", "search_node_" + System.currentTimeMillis());
        String zkAddress = SearchNodeConfig.ZK_ADDRESS;
        String clusterName = SearchNodeConfig.CLUSTER_NAME;

        System.out.println("DEBUG: Starting Search Node: " + nodeId);
        System.out.println("DEBUG: Connecting to ZK: " + zkAddress + " Cluster: " + clusterName);

        try {
            System.out.println("DEBUG: Connecting to Helix...");
            HelixManager manager = HelixManagerFactory.getZKHelixManager(
                    clusterName,
                    nodeId,
                    InstanceType.PARTICIPANT,
                    zkAddress);

            System.out.println("DEBUG: Registering state model factory...");
            manager.getStateMachineEngine().registerStateModelFactory(
                    SearchNodeConfig.STATE_MODEL,
                    new SearchNodeStateModelFactory());

            System.out.println("DEBUG: Connection initiate...");
            manager.connect();

            System.out.println("DEBUG: Node " + nodeId + " CONNECTED SUCCESS!");

            // Keep process alive
            while (true) {
                Thread.sleep(10000);
                System.out.println("DEBUG: Node " + nodeId + " is alive.");
            }
        } catch (Exception e) {
            System.err.println("DEBUG ERROR in Search Node [" + nodeId + "]");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
