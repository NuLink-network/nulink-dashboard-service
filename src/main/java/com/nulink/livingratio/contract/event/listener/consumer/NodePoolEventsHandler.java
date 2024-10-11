package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.constant.NodePoolEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.entity.event.*;
import com.nulink.livingratio.service.*;
import com.nulink.livingratio.utils.EthLogsParser;
import com.nulink.livingratio.utils.NodePoolMapSingleton;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Component
public class NodePoolEventsHandler {

    private static Web3jUtils web3jUtils;

    private static NodePoolEventsService nodePoolEventsService;

    private static EpochFeeRateEventService epochFeeRateEventService;

    public NodePoolEventsHandler(Web3jUtils web3jUtils,
                                 NodePoolEventsService nodePoolEventsService,
                                 EpochFeeRateEventService epochFeeRateEventService) {
        NodePoolEventsHandler.web3jUtils = web3jUtils;
        NodePoolEventsHandler.nodePoolEventsService = nodePoolEventsService;
        NodePoolEventsHandler.epochFeeRateEventService = epochFeeRateEventService;
    }

    public static void descStaking(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.STAKE_DESC);

        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();

        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents stake = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(NodePoolEventEnum.STAKING.getName());
            stake.setUser(EthLogsParser.hexToAddress(topics.get(1)));
            stake.setAmount(args.get(0).getValue().toString());
            stake.setEpoch(args.get(1).getValue().toString());
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                stake.setTokenId(createNodePoolEvent.getTokenId());
            }
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            //nodePoolEventsService.deleteAfterEvents(stake.getUser(), eventHappenedTimeStamp);
            nodePoolEventsService.save(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descUnStaking(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.UN_STAKE_ALL_DESC);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents stake = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(NodePoolEventEnum.UN_STAKING.getName());
            stake.setUser(EthLogsParser.hexToAddress(topics.get(1)));
            stake.setUnlockAmount(args.get(0).getValue().toString());
            stake.setLockAmount(args.get(1).getValue().toString());
            stake.setEpoch(args.get(2).getValue().toString());
            stake.setAmount((new BigInteger(stake.getUnlockAmount()).add(new BigInteger(stake.getLockAmount()))).toString());
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                stake.setTokenId(createNodePoolEvent.getTokenId());
            }
           // nodePoolEventsService.deleteAfterEvents(stake.getUser(), eventHappenedTimeStamp);
            nodePoolEventsService.save(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaim(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM_DESC);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents claim = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claim.setTxHash(transactionHash);
            claim.setUser(EthLogsParser.hexToAddress(topics.get(1)));
            claim.setAmount(args.get(0).getValue().toString());
            claim.setEpoch(args.get(1).getValue().toString());
            claim.setEvent(NodePoolEventEnum.CLAIM.getName());
            claim.setCreateTime(eventHappenedTimeStamp);
            claim.setLastUpdateTime(eventHappenedTimeStamp);
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                claim.setTokenId(createNodePoolEvent.getTokenId());
            }
            //nodePoolEventsService.deleteAfterEvents(claim.getUser(), eventHappenedTimeStamp);
            nodePoolEventsService.save(claim);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaimReward(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM_REWARD_DESC);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents claimReward = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claimReward.setTxHash(transactionHash);
            claimReward.setUser(EthLogsParser.hexToAddress(topics.get(1)));
            claimReward.setAmount(args.get(0).getValue().toString());
            claimReward.setLastRewardEpoch(args.get(1).getValue().toString());
            claimReward.setEpoch(args.get(2).getValue().toString());
            claimReward.setEvent(NodePoolEventEnum.CLAIM_REWARD.getName());
            claimReward.setCreateTime(eventHappenedTimeStamp);
            claimReward.setLastUpdateTime(eventHappenedTimeStamp);
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                claimReward.setTokenId(createNodePoolEvent.getTokenId());
            }
            //nodePoolEventsService.deleteAfterEvents(claimReward.getUser(), eventHappenedTimeStamp);
            nodePoolEventsService.save(claimReward);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descClaimReward from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descSetNextEpochFeeRate(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.SET_NEXT_EPOCH_FEE_RATE_DESC);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            EpochFeeRateEvent event = new EpochFeeRateEvent();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            event.setTxHash(transactionHash);
            BigInteger epoch = EthLogsParser.hexToBigInteger(topics.get(1));
            if (epoch != null) {
                event.setEpoch(epoch.toString());
            }
            event.setFeeRate(args.get(0).getValue().toString());
            event.setCreateTime(eventHappenedTimeStamp);
            event.setLastUpdateTime(eventHappenedTimeStamp);
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                event.setTokenId(createNodePoolEvent.getTokenId());
            }
            epochFeeRateEventService.save(event);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }
}
