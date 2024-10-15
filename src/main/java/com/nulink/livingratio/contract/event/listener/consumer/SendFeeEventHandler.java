package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.event.Bond;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.entity.event.SendFeeEvent;
import com.nulink.livingratio.service.BondService;
import com.nulink.livingratio.service.SendFeeEventService;
import com.nulink.livingratio.utils.EthLogsParser;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.Log;

import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Component
public class SendFeeEventHandler {

    private static SendFeeEventService sendFeeEventService;
    private static Web3jUtils web3jUtils;

    public SendFeeEventHandler(SendFeeEventService sendFeeEventService, Web3jUtils web3jUtils) {
        SendFeeEventHandler.sendFeeEventService = sendFeeEventService;
        SendFeeEventHandler.web3jUtils = web3jUtils;
    }

    public static void descSendFeeEvent(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.SEND_FEE_DESC);

        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        SendFeeEvent sendFeeEvent = new SendFeeEvent();
        String transactionHash = evLog.getTransactionHash();
        Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
        sendFeeEvent.setTxHash(transactionHash);
        sendFeeEvent.setTokenId(EthLogsParser.hexToBigInteger(topics.get(1)).toString());
        sendFeeEvent.setEpoch(args.get(0).getValue().toString());
        sendFeeEvent.setUser(args.get(1).getValue().toString());
        sendFeeEvent.setAmount(args.get(2).getValue().toString());
        sendFeeEvent.setCreateTime(eventHappenedTimeStamp);
        sendFeeEvent.setLastUpdateTime(eventHappenedTimeStamp);
        sendFeeEventService.save(sendFeeEvent);
    }
}
