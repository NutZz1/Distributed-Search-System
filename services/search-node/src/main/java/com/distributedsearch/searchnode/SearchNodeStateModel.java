package com.distributedsearch.searchnode;

import org.apache.helix.NotificationContext;
import org.apache.helix.model.Message;
import org.apache.helix.participant.statemachine.StateModel;
import org.apache.helix.participant.statemachine.StateModelInfo;
import org.apache.helix.participant.statemachine.Transition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@StateModelInfo(states = "{'MASTER', 'SLAVE', 'OFFLINE'}", initialState = "OFFLINE")
public class SearchNodeStateModel extends StateModel {
    private static final Logger logger = LoggerFactory.getLogger(SearchNodeStateModel.class);
    private final String partitionName;

    public SearchNodeStateModel(String partitionName) {
        this.partitionName = partitionName;
    }

    @Transition(from = "OFFLINE", to = "SLAVE")
    public void onBecomeSlaveFromOffline(Message message, NotificationContext context) {
        String instanceName = context.getManager().getInstanceName();
        System.out.println("\n[SHARD STATUS] ---------------------------------------------");
        System.out.println("[SHARD STATUS] " + partitionName + " assigned to " + instanceName + " as SLAVE (Follower)");
        System.out.println("[SHARD STATUS] ---------------------------------------------\n");
        logger.info("[{}] {} transitioning OFFLINE -> SLAVE (Follower)", instanceName, partitionName);
    }

    @Transition(from = "SLAVE", to = "MASTER")
    public void onBecomeMasterFromSlave(Message message, NotificationContext context) {
        String instanceName = context.getManager().getInstanceName();
        System.out.println("\n[LEADER ELECTED] *******************************************");
        System.out.println("[LEADER ELECTED] " + partitionName + " Promoting to MASTER (Leader) on " + instanceName);
        System.out.println("[LEADER ELECTED] *******************************************\n");
        logger.info("[{}] {} transitioning SLAVE -> MASTER (Leader)", instanceName, partitionName);
    }

    @Transition(from = "MASTER", to = "SLAVE")
    public void onBecomeSlaveFromMaster(Message message, NotificationContext context) {
        String instanceName = context.getManager().getInstanceName();
        logger.info("==================================================");
        logger.info("  FAILOVER/REBALANCE: {} demoted MASTER -> SLAVE", partitionName);
        logger.info("  Instance: {}", instanceName);
        logger.info("==================================================");
        logger.info("[{}] {} transitioning MASTER -> SLAVE (demotion)", instanceName, partitionName);
    }

    @Transition(from = "SLAVE", to = "OFFLINE")
    public void onBecomeOfflineFromSlave(Message message, NotificationContext context) {
        String instanceName = context.getManager().getInstanceName();
        logger.info("[{}] {} transitioning SLAVE -> OFFLINE", instanceName, partitionName);
    }

    @Transition(from = "OFFLINE", to = "DROPPED")
    public void onBecomeDroppedFromOffline(Message message, NotificationContext context) {
        String instanceName = context.getManager().getInstanceName();
        logger.info("[{}] {} transitioning OFFLINE -> DROPPED", instanceName, partitionName);
    }
}
