package com.nulink.livingratio.contract.event.listener.filter.events.impl;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.EventBuilder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;

import java.util.Arrays;
import java.util.List;

public class ContractsEventBuilder implements EventBuilder<ContractsEventEnum> {

    @Override
    public Event build(ContractsEventEnum type) {
        switch (type) {
            case STAKE:
                return getStakeEvent();
            case UN_STAKE_ALL:
                return getUnStakeAllEvent();
            case OPERATOR_BONDED:
                return getOperatorBondedEvent();
            case CLAIM:
                return getCliamEvent();
            case CLAIM_REWARD:
                return getCliamRewardEvent();
            case SET_NEXT_EPOCH_FEE_RATE:
                return getSetNextEpochFeeRateEvent();
            case SET_NEXT_EPOCH_FEE_RATE_DESC:
                return getSetNextEpochFeeRateEventDesc();
            case CREATE_NODE_POOL:
                return getCreateNodePoolEvent();
            case CREATE_NODE_POOL_DESC:
                 return getCreateNodePoolDescEvent();
            default:
                return null;
        }
    }

    public static Event getStakeEvent() {
        return new Event("Staking",
                Arrays.asList(
                        // _user
                        new TypeReference<Address>(false) {},
                        // _amount
                        new TypeReference<Uint256>(false) {},
                        // _epoch
                        new TypeReference<Uint16>(false) {}
                ));
    }

    public static Event getUnStakeAllEvent() {
        return new Event("UnStaking",
                Arrays.asList(
                        // _user
                        new TypeReference<Address>(false) {},
                        // _unlockAmount
                        new TypeReference<Uint256>(false) {},
                        // _lockAmount
                        new TypeReference<Uint256>(false) {},
                        // _epoch
                        new TypeReference<Uint16>(false) {}
                ));
    }

    public static Event getOperatorBondedEvent() {
        return new Event("OperatorBonded",
                Arrays.asList(
                        // stakingProvider
                        new TypeReference<Address>(true) {},
                        // operator
                        new TypeReference<Address>(true) {},
                        // startTimestamp
                        new TypeReference<Uint256>(true) {}
                ));
    }

    public static Event getCliamEvent() {
        return new Event("Claim",
                Arrays.asList(
                        // user
                        new TypeReference<Address>(true) {},
                        // amount
                        new TypeReference<Uint256>(true) {},
                        // epoch
                        new TypeReference<Uint16>(true) {}
                ));
    }

    public static Event getCliamRewardEvent() {
        return new Event("ClaimReward",
                Arrays.asList(
                        // user
                        new TypeReference<Address>(true) {},
                        // _amount
                        new TypeReference<Uint256>(true) {},
                        // _lastRewardEpoch
                        new TypeReference<Uint256>(true) {},
                        // _lastEpoch
                        new TypeReference<Uint16>(true) {}
                ));
    }

    public static Event getSetNextEpochFeeRateEvent() {
        return new Event("SetNextEpochFeeRate",
                Arrays.asList(
                        // _epoch
                        new TypeReference<Uint16>(true) {},
                        // _feeRate
                        new TypeReference<Uint256>(true) {}
                ));
    }

    public static Event getSetNextEpochFeeRateEventDesc() {
        return new Event("SetNextEpochFeeRate",
                List.of(
                        // _epoch
                        //new TypeReference<Uint16>(true) {},
                        // _feeRate
                        new TypeReference<Uint256>(true) {
                        }
                ));
    }

    public static Event getCreateNodePoolEvent() {
        return new Event("CreateNodePool",
                Arrays.asList(
                        // _nodePoolAddr
                        new TypeReference<Address>(true) {},
                        // _tokenID
                        new TypeReference<Uint256>(true) {},
                        // _owner
                        new TypeReference<Address>(true) {}
                ));
    }

    public static Event getCreateNodePoolDescEvent() {
        return new Event("CreateNodePool",
                Arrays.asList(
                        // _tokenID
                        new TypeReference<Uint256>(true) {},
                        // _owner
                        new TypeReference<Address>(true) {}
                ));
    }
}
