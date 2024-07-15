package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.constant.NodePoolEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.NodePoolEvents;
import com.nulink.livingratio.entity.event.Claim;
import com.nulink.livingratio.entity.event.ClaimReward;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.entity.event.StakingEvent;
import com.nulink.livingratio.service.ClaimRewardService;
import com.nulink.livingratio.service.ClaimService;
import com.nulink.livingratio.service.NodePoolEventsService;
import com.nulink.livingratio.service.PersonalStakeService;
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

    public NodePoolEventsHandler(Web3jUtils web3jUtils, NodePoolEventsService nodePoolEventsService) {
        NodePoolEventsHandler.web3jUtils = web3jUtils;
        NodePoolEventsHandler.nodePoolEventsService = nodePoolEventsService;
    }

    public static void descStaking(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.STAKE);

        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();

        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents stake = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(NodePoolEventEnum.STAKING.getName());
            stake.setUser(args.get(0).getValue().toString());
            stake.setAmount(args.get(1).getValue().toString());
            stake.setEpoch(args.get(2).getValue().toString());
            CreateNodePoolEvent createNodePoolEvent = NodePoolMapSingleton.get(evLog.getAddress());
            if (createNodePoolEvent != null){
                stake.setTokenId(createNodePoolEvent.getTokenId());
            }
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            nodePoolEventsService.save(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descUnStaking(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.UN_STAKE_ALL);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents stake = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(NodePoolEventEnum.UN_STAKING.getName());
            stake.setUser(args.get(0).getValue().toString());
            stake.setUnlockAmount(args.get(1).getValue().toString());
            stake.setLockAmount(args.get(2).getValue().toString());
            stake.setEpoch(args.get(3).getValue().toString());
            stake.setAmount((new BigInteger(stake.getUnlockAmount()).add(new BigInteger(stake.getLockAmount()))).toString());
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            nodePoolEventsService.save(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaim(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents claim = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claim.setTxHash(transactionHash);
            claim.setUser(args.get(0).getValue().toString());
            claim.setAmount(args.get(1).getValue().toString());
            claim.setEpoch(args.get(2).getValue().toString());
            claim.setEpoch(NodePoolEventEnum.CLAIM.getName());
            claim.setCreateTime(eventHappenedTimeStamp);
            claim.setLastUpdateTime(eventHappenedTimeStamp);
            nodePoolEventsService.save(claim);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaimReward(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            NodePoolEvents claimReward = new NodePoolEvents();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claimReward.setTxHash(transactionHash);
            claimReward.setUser(args.get(0).getValue().toString());
            claimReward.setAmount(args.get(1).getValue().toString());
            claimReward.setLastRewardEpoch(args.get(2).getValue().toString());
            claimReward.setEpoch(args.get(3).getValue().toString());
            claimReward.setEvent(NodePoolEventEnum.CLAIM_REWARD.getName());
            claimReward.setCreateTime(eventHappenedTimeStamp);
            claimReward.setLastUpdateTime(eventHappenedTimeStamp);
            nodePoolEventsService.save(claimReward);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }
}
