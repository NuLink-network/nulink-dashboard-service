package com.nulink.livingratio.contract.event.listener.impl;

import com.nulink.livingratio.config.ContractsConfig;
import com.nulink.livingratio.contract.event.listener.consumer.BondEventHandler;
import com.nulink.livingratio.contract.event.listener.consumer.CreateNodePoolEventHandler;
import com.nulink.livingratio.contract.event.listener.consumer.NodePoolEventsHandler;
import com.nulink.livingratio.contract.event.listener.consumer.SendFeeEventHandler;
import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.ContractOffset;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.service.ContractOffsetService;
import com.nulink.livingratio.utils.NodePoolMapSingleton;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BlockEventListener {

    public static Logger logger = LoggerFactory.getLogger(BlockEventListener.class);

    private final static BigInteger STEP = new BigInteger("10");

    public static final String BLOCK_CONTRACT_FLAG = "BLOCK_CONTRACT_FLAG";

    @Autowired
    ContractsConfig contractsConfig;

    @Autowired
    private Web3jUtils web3jUtils;

    @Autowired
    private ContractOffsetService contractOffsetService;

    @Value("${contracts.start}")
    public String scannerContractStart;

    private Map<String, Event> topicAndContractAddr2EventMap = new HashMap<>();

    private Map<String, Method> topicAndContractAddr2CallBackMap = new HashMap<>();


    @Value("${contracts.enabled}")
    private boolean enabled;


    public void start(Integer delayBlocks, Set<String> enablesTaskNames, Set<String> disableTaskNames) throws InterruptedException, NoSuchMethodException {
        if (ObjectUtils.isEmpty(delayBlocks)) {
            delayBlocks = 0;
        }

        if (!enabled) {
            log.info("Delay" + delayBlocks + "_" + "BlockEventListener is disabled! ........");
            return;
        }
        logger.info("Delay" + delayBlocks + "_" + "BlockEventListener start");
        initialize(enablesTaskNames, disableTaskNames);
        blocksEventScanner(delayBlocks);
        logger.info("Delay" + delayBlocks + "_" + "BlockEventListener end");
    }

    private boolean isTaskEnable(Set<String> enablesTaskNames, Set<String> disableTaskNames, String curTaskName) {
        curTaskName = curTaskName.toLowerCase();

        boolean disableTaskNamesIsNull = ObjectUtils.isEmpty(disableTaskNames);
        boolean enablesTaskNamesIsNull = ObjectUtils.isEmpty(enablesTaskNames);

        if (disableTaskNamesIsNull && enablesTaskNamesIsNull) {
            return true;
        }
        else if (!disableTaskNamesIsNull && !enablesTaskNamesIsNull) {
            return true;
        } else if (!disableTaskNamesIsNull && !disableTaskNames.contains(curTaskName)) {
            return true;
        } else if (!enablesTaskNamesIsNull && enablesTaskNames.contains(curTaskName)) {
            return true;
        }
        return false;

    }

    public void initialize(Set<String> enablesTaskNames, Set<String> disableTaskNames) throws NoSuchMethodException {
        if (ObjectUtils.isEmpty(enablesTaskNames)) {
            enablesTaskNames = new HashSet<>();
        } else {
            enablesTaskNames = enablesTaskNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        }

        if (ObjectUtils.isEmpty(disableTaskNames)) {
            disableTaskNames = new HashSet<>();
        } else {
            disableTaskNames = disableTaskNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        }


        topicAndContractAddr2EventMap.clear();
        topicAndContractAddr2CallBackMap.clear();


        ContractsConfig.ContractInfo stakingManagerCI = contractsConfig.getContractInfo("NodePoolStakingManager");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, stakingManagerCI.getName()) && stakingManagerCI.getEnabled()) {
            Event operatorBonded = new ContractsEventBuilder().build(ContractsEventEnum.OPERATOR_BONDED);
            String topicEventBuyBlindBox = EventEncoder.encode(operatorBonded).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventBuyBlindBox + "_" + stakingManagerCI.getAddress(), operatorBonded);
            topicAndContractAddr2CallBackMap.put(topicEventBuyBlindBox + "_" + stakingManagerCI.getAddress(), BondEventHandler.class.getMethod("descOperatorBonded", Log.class /*,secondParameterTypeClass.class*/));
        }

        ContractsConfig.ContractInfo nodePoolFactoryCI = contractsConfig.getContractInfo("NodePoolFactory");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, nodePoolFactoryCI.getName()) && nodePoolFactoryCI.getEnabled()) {
            Event createNodePoolEvent = new ContractsEventBuilder().build(ContractsEventEnum.CREATE_NODE_POOL);
            String createNodePoolEventTopic = EventEncoder.encode(createNodePoolEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(createNodePoolEventTopic + "_" + nodePoolFactoryCI.getAddress(), createNodePoolEvent);
            topicAndContractAddr2CallBackMap.put(createNodePoolEventTopic + "_" + nodePoolFactoryCI.getAddress(), CreateNodePoolEventHandler.class.getMethod("descCreateNodePool", Log.class /*,secondParameterTypeClass.class*/));
        }

        ContractsConfig.ContractInfo nodePoolRouterCI = contractsConfig.getContractInfo("NodePoolVaultProxy");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, nodePoolRouterCI.getName()) && nodePoolRouterCI.getEnabled()) {
            Event sendFeeEvent = new ContractsEventBuilder().build(ContractsEventEnum.SEND_FEE);
            String sendFeeEventTopic = EventEncoder.encode(sendFeeEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(sendFeeEventTopic + "_" + nodePoolRouterCI.getAddress(), sendFeeEvent);
            topicAndContractAddr2CallBackMap.put(sendFeeEventTopic + "_" + nodePoolRouterCI.getAddress(), SendFeeEventHandler.class.getMethod("descSendFeeEvent", Log.class));
        }

        initNodePoolEvent();
    }

    public void initNodePoolEvent() throws NoSuchMethodException{
        Map<String, CreateNodePoolEvent> sharedMap = NodePoolMapSingleton.getSharedMap();
        for (Map.Entry<String, CreateNodePoolEvent> entry : sharedMap.entrySet()) {
            List<String> enabledContractAddresses = contractsConfig.getEnabledContractAddresses();
            if (!enabledContractAddresses.contains(entry.getValue().getNodePoolAddress().toLowerCase())) {
                ContractsConfig.ContractInfo contractInfo = new ContractsConfig.ContractInfo();
                contractInfo.setAddress(entry.getValue().getNodePoolAddress());
                contractInfo.setEnabled(true);
                contractsConfig.getContractList().add(contractInfo);
            }
            String key = entry.getKey();
            Event stakeEvent = new ContractsEventBuilder().build(ContractsEventEnum.STAKE);
            String topicEventTransfer = EventEncoder.encode(stakeEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventTransfer + "_" + key, stakeEvent);
            topicAndContractAddr2CallBackMap.put(topicEventTransfer + "_" + key, NodePoolEventsHandler.class.getMethod("descStaking", Log.class));

            Event unStakeAllEvent = new ContractsEventBuilder().build(ContractsEventEnum.UN_STAKE_ALL);
            String topicEventMint = EventEncoder.encode(unStakeAllEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventMint + "_" + key, unStakeAllEvent);
            topicAndContractAddr2CallBackMap.put(topicEventMint + "_" + key, NodePoolEventsHandler.class.getMethod("descUnStaking", Log.class));

            Event claimEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
            String topicEventClaim = EventEncoder.encode(claimEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventClaim + "_" + key, claimEvent);
            topicAndContractAddr2CallBackMap.put(topicEventClaim + "_" + key, NodePoolEventsHandler.class.getMethod("descClaim", Log.class));

            Event claimRewardEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM_REWARD);
            String topicEventClaimReward = EventEncoder.encode(claimRewardEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventClaimReward + "_" + key, claimRewardEvent);
            topicAndContractAddr2CallBackMap.put(topicEventClaimReward + "_" + key, NodePoolEventsHandler.class.getMethod("descClaimReward", Log.class));

            Event setFeeRatioEvent = new ContractsEventBuilder().build(ContractsEventEnum.SET_NEXT_EPOCH_FEE_RATE);
            String setFeeRatioEventTopic = EventEncoder.encode(setFeeRatioEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(setFeeRatioEventTopic + "_" + key, setFeeRatioEvent);
            topicAndContractAddr2CallBackMap.put(setFeeRatioEventTopic + "_" + key, NodePoolEventsHandler.class.getMethod("descSetNextEpochFeeRate", Log.class));
        }
    }

    public void blocksEventScanner(Integer delayBlocks) throws InterruptedException, NoSuchMethodException {

        ContractOffset contractOffset = contractOffsetService.findByContractAddress("Delay" + delayBlocks + "_" + BLOCK_CONTRACT_FLAG);
        BigInteger start;
        if (contractOffset == null) {
            start = new BigInteger(scannerContractStart);
        } else {
            start = contractOffset.getBlockOffset();
            if (ObjectUtils.isEmpty(start) || start.compareTo(BigInteger.ZERO) == 0) {
                start = new BigInteger(scannerContractStart);
            }
        }

        logger.info("Delay" + delayBlocks + "_ scan block " + " : " + start);

        BigInteger now = web3jUtils.getBlockNumber(delayBlocks);

        if (start.compareTo(now) >= 0) {
            logger.info("Delay{}_scan block return, start >= now: {} >= {}", delayBlocks, start, now);
            return;
        }

        while (true) {

            if (now.compareTo(BigInteger.ZERO) == 0) {
                logger.info("Delay{}_scan block return,  now is Zero", delayBlocks);
                break;
            }

            BigInteger end = start.add(STEP).compareTo(now) > 0 ? now : start.add(STEP);

            logger.info("Delay{}_blocksEventScanner run block [{},{}] ", delayBlocks, start, end);


            filterEvents(delayBlocks, start, end);

            start = end;

            contractOffsetService.updateOffset(contractOffset, delayBlocks, end);

            if (end.compareTo(now) >= 0) {
                logger.info("Delay" + delayBlocks + "_" + "scan block return, end >= now: " + end + " >= " + now);
                break;
            } else {
                initialize(null, null);
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    private void filterEvents(Integer delayBlocks, BigInteger start, BigInteger end) {


        List<Event> events = new ArrayList<>(topicAndContractAddr2EventMap.values());

        try {
            EthLog ethlog = web3jUtils.getEthLogs(start, end, events, contractsConfig.getEnabledContractAddresses()/*can be null */);
            logger.info("Delay" + delayBlocks + "_" + "filterEvents size: " + ethlog.getLogs().size());
            if (!ObjectUtils.isEmpty(ethlog) && ethlog.getLogs().size() > 0) {
                eventDispatcher(delayBlocks, ethlog);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void eventDispatcher(Integer delayBlocks, EthLog logs) {
        for (EthLog.LogResult logResult : logs.getLogs()) {

            Log log = (Log) logResult.get();

            String contractAddress = log.getAddress().toLowerCase();

            String topic = null;
            try {
                topic = log.getTopics().get(0).toLowerCase();
            } catch (Exception e) {
                continue;
            }

            String topicAddress = topic + "_" + contractAddress;
            Method callBackMethod = topicAndContractAddr2CallBackMap.get(topicAddress);
            if (null == callBackMethod) {
                continue;
            }
            try {
                //https://stackoverflow.com/questions/4480334/how-to-call-a-method-stored-in-a-hashmap-java
                // Method format must be: static void functionName(Log, Album)
                logger.info("Delay" + delayBlocks + "_" + "eventDispatcher call function: {} ", callBackMethod.getName());
                callBackMethod.invoke(null, log);

            } catch (Exception e) {
                logger.info("Delay" + delayBlocks + "_" + "scan block function {} exception: {}", callBackMethod.getName(), e.getMessage());
            }

        }

    }

    /*private void updateOffset(Integer delayBlocks, BigInteger offset) {

        String contractAddress = "Delay" + delayBlocks + "_" + BLOCK_CONTRACT_FLAG;

        ContractOffset contractOffset = contractOffsetService.findByContractAddress(contractAddress);
        if (null == contractOffset) {
            contractOffset = new ContractOffset();
            contractOffset.setContractAddress(contractAddress);
            contractOffset.setContractName("ALL_CONTRACTS");
            contractOffset.setRecordedAt(new Timestamp(new Date().getTime()));
        }
        contractOffset.setBlockOffset(offset);
        contractOffsetService.update(contractOffset);
    }*/

}
