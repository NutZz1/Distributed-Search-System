package com.distributedsearch.searchnode;

import org.apache.helix.participant.statemachine.StateModel;
import org.apache.helix.participant.statemachine.StateModelFactory;

public class SearchNodeStateModelFactory extends StateModelFactory<SearchNodeStateModel> {
    @Override
    public SearchNodeStateModel createNewStateModel(String resourceName, String partitionName) {
        return new SearchNodeStateModel(partitionName);
    }
}
