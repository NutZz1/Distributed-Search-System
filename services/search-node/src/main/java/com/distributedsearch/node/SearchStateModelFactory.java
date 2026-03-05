package com.distributedsearch.node;

import org.apache.helix.NotificationContext;
import org.apache.helix.model.Message;
import org.apache.helix.participant.statemachine.StateModel;
import org.apache.helix.participant.statemachine.StateModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating state models to handle partition state transitions.
 * Manages OFFLINE -> SLAVE -> MASTER transitions for search index partitions.
 */
public class SearchStateModelFactory extends StateModelFactory<StateModel> {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchStateModelFactory.class);
    private final SearchService searchService;

    public SearchStateModelFactory(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public StateModel createNewStateModel(String resourceName, String partitionName) {
        logger.info("Creating state model for resource: {}, partition: {}", resourceName, partitionName);
        return new SearchStateModel(partitionName, searchService);
    }

    /**
     * State model that handles transitions for a specific partition.
     * Implements the MasterSlave state model.
     */
    public static class SearchStateModel extends StateModel {
        
        private static final Logger logger = LoggerFactory.getLogger(SearchStateModel.class);
        private final String partitionName;
        private final SearchService searchService;

        public SearchStateModel(String partitionName, SearchService searchService) {
            this.partitionName = partitionName;
            this.searchService = searchService;
        }

        /**
         * Transition: OFFLINE -> SLAVE
         * Node becomes a slave replica for this partition.
         */
        public void onBecomeSlaveFromOffline(Message message, NotificationContext context) {
            logger.info("⬆️ OFFLINE → SLAVE for partition: {}", partitionName);
            searchService.addPartition(partitionName, false);
        }

        /**
         * Transition: SLAVE -> MASTER
         * Node is promoted to master for this partition.
         */
        public void onBecomeMasterFromSlave(Message message, NotificationContext context) {
            logger.info("⬆️⬆️ SLAVE → MASTER for partition: {}", partitionName);
            searchService.promoteToMaster(partitionName);
        }

        /**
         * Transition: MASTER -> SLAVE
         * Node is demoted from master to slave (e.g., during rebalance).
         */
        public void onBecomeSlaveFromMaster(Message message, NotificationContext context) {
            logger.info("⬇️ MASTER → SLAVE for partition: {}", partitionName);
            searchService.demoteToSlave(partitionName);
        }

        /**
         * Transition: SLAVE -> OFFLINE
         * Node stops serving this partition.
         */
        public void onBecomeOfflineFromSlave(Message message, NotificationContext context) {
            logger.info("⬇️⬇️ SLAVE → OFFLINE for partition: {}", partitionName);
            searchService.removePartition(partitionName);
        }

        /**
         * Transition: OFFLINE -> MASTER (rare, but can happen)
         * Node directly becomes master without being slave first.
         */
        public void onBecomeMasterFromOffline(Message message, NotificationContext context) {
            logger.info("⬆️⬆️ OFFLINE → MASTER for partition: {}", partitionName);
            searchService.addPartition(partitionName, true);
        }

        /**
         * Transition: MASTER -> OFFLINE (e.g., node being removed)
         * Node stops being master and goes offline.
         */
        public void onBecomeOfflineFromMaster(Message message, NotificationContext context) {
            logger.info("⬇️⬇️ MASTER → OFFLINE for partition: {}", partitionName);
            searchService.removePartition(partitionName);
        }

        /**
         * Transition: * -> DROPPED
         * Partition is being removed from this node.
         */
        public void onBecomeDroppedFromOffline(Message message, NotificationContext context) {
            logger.info("❌ OFFLINE → DROPPED for partition: {}", partitionName);
            searchService.removePartition(partitionName);
        }
    }
}
