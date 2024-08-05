package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.utils.EthLogsParser;
import org.springframework.util.CollectionUtils;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.Log;
import com.nulink.livingratio.service.CreateNodePoolEventService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Component
public class CreateNodePoolEventHandler {

    private static Web3jUtils web3jUtils;

    private static CreateNodePoolEventService createNodePoolEventService;

    public CreateNodePoolEventHandler(Web3jUtils web3jUtils, CreateNodePoolEventService createNodePoolEventService) {
        CreateNodePoolEventHandler.web3jUtils = web3jUtils;
        CreateNodePoolEventHandler.createNodePoolEventService = createNodePoolEventService;
    }

    public static void descCreateNodePool(Log evLog) {
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CREATE_NODE_POOL_DESC);

        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();

        if (!CollectionUtils.isEmpty(args)) {
            CreateNodePoolEvent createNodePoolEvent = new CreateNodePoolEvent();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            createNodePoolEvent.setTxHash(transactionHash);
            createNodePoolEvent.setNodePoolAddress(EthLogsParser.hexToAddress(topics.get(1)));
            createNodePoolEvent.setTokenId(args.get(0).getValue().toString());
            createNodePoolEvent.setOwnerAddress(args.get(1).getValue().toString());
            createNodePoolEvent.setCreateTime(eventHappenedTimeStamp);
            createNodePoolEvent.setLastUpdateTime(eventHappenedTimeStamp);
            createNodePoolEventService.save(createNodePoolEvent);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }
}
