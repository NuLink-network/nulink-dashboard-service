package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.repository.*;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GridStakingDetailService {

    private static final String DESC_SORT = "desc";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    private final GridStakingDetailRepository gridStakingDetailRepository;

    private final GridStakeRewardRepository gridStakeRewardRepository;

    private final ContractOffsetService contractOffsetService;

    private final EpochFeeRateEventService epochFeeRateEventService;

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    private final CreateNodePoolEventService createNodePoolEventService;

    private final RedisService redisService;

    private final Web3jUtils web3jUtils;
    @Autowired
    private NodePoolEventsRepository nodePoolEventsRepository;

    public GridStakingDetailService(GridStakingDetailRepository gridStakingDetailRepository,
                                    GridStakeRewardRepository gridStakeRewardRepository,
                                    ContractOffsetService contractOffsetService,
                                    EpochFeeRateEventService epochFeeRateEventService,
                                    ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository,
                                    CreateNodePoolEventService createNodePoolEventService,
                                    RedisService redisService,
                                    Web3jUtils web3jUtils) {
        this.gridStakingDetailRepository = gridStakingDetailRepository;
        this.gridStakeRewardRepository = gridStakeRewardRepository;
        this.contractOffsetService = contractOffsetService;
        this.epochFeeRateEventService = epochFeeRateEventService;
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
        this.createNodePoolEventService = createNodePoolEventService;
        this.redisService = redisService;
        this.web3jUtils = web3jUtils;
    }

    public void create(GridStakingDetail gridStakingDetail) {
        gridStakingDetailRepository.save(gridStakingDetail);
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void generatePersonalCurrentEpochStakeRewardTask(){
        RLock personalCurrentEpochStakeRewardTaskLock = redissonClient.getLock("PersonalCurrentEpochStakeRewardTask");
        try{
            if (personalCurrentEpochStakeRewardTaskLock.tryLock()){
                log.info("The generate Personal Current Epoch Valid StakeReward task is beginning");
                String currentEpoch = web3jUtils.getCurrentEpoch();
                if (Integer.parseInt(currentEpoch) < 1) {
                    return;
                }

                /*if (!checkScanBlockNumber()){
                    log.info("Waiting for scanning block");
                    return;
                }*/

                List<CreateNodePoolEvent> nodePools = createNodePoolEventService.findAll();
                ExecutorService executorService = Executors.newFixedThreadPool((Math.min(nodePools.size(), 10)));

                for (CreateNodePoolEvent pool : nodePools) {
                    executorService.submit(() -> generatePersonalCurrentEpochValidStakeReward(pool, currentEpoch));
                }

                executorService.shutdown();
                try {
                    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error( "The generate Personal Current Epoch Valid StakeReward task fail: ", e);
                }
                log.info("The generate Personal  Current Epoch Valid StakeReward task is finish");
            } else {
                log.info("The generate Personal Current Epoch Valid StakeReward task is already in progress");
            }
        }catch (Exception e){
            log.error("The generate Personal Current Epoch Valid StakeReward task fail:{}", e);
        }finally {
            personalCurrentEpochStakeRewardTaskLock.unlock();
        }
    }


    public void generatePersonalCurrentEpochValidStakeReward(CreateNodePoolEvent grid, String currentEpoch){
        String tokenId = grid.getTokenId();
        RLock lock = redissonClient.getLock("PersonalCurrentEpochValidStakeReward" + tokenId + currentEpoch);
        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        try{
            if (lock.tryLock()){
                List<GridStakingDetail> stakingDetails = gridStakingDetailRepository.findByEpochAndTokenId(tokenId, currentEpoch);
                if (!stakingDetails.isEmpty()){
                    log.info("Grid {} Current Epoch Valid StakeReward task has already been executed.", tokenId);
                    platformTransactionManager.commit(status);
                    return;
                }
                // Get the valid personal staking amount
                List<ValidPersonalStakingAmount> personalStakingAmounts = validPersonalStakingAmountRepository.findAllByTokenIdAndEpochLessThanEqual(tokenId, Integer.parseInt(currentEpoch));
                /*if (personalStakingAmounts.isEmpty()){
                    log.info("Grid {} valid personal staking amount is empty", tokenId);
                    platformTransactionManager.commit(status);
                    return;
                }*/
                String currentFeeRate = epochFeeRateEventService.getFeeRate(tokenId, currentEpoch);
                String nextFeeRate = epochFeeRateEventService.getFeeRate(tokenId, String.valueOf(Integer.parseInt(currentEpoch) + 1));
                personalStakingAmounts = personalStakingAmounts.stream().filter(personalStakingAmount -> !personalStakingAmount.getStakingAmount().equals("0")).collect(Collectors.toList());
                BigInteger totalStakingAmount = BigInteger.ZERO;
                for (ValidPersonalStakingAmount personalStakingAmount : personalStakingAmounts) {
                    totalStakingAmount = totalStakingAmount.add(new BigInteger(personalStakingAmount.getStakingAmount()));
                }
                List<GridStakingDetail> gridStakingDetails = new ArrayList<>();
                for (ValidPersonalStakingAmount personalStakingAmount : personalStakingAmounts) {
                    GridStakingDetail gridStakingDetail = new GridStakingDetail();
                    gridStakingDetail.setEpoch(currentEpoch);
                    gridStakingDetail.setTokenId(personalStakingAmount.getTokenId());
                    gridStakingDetail.setUserAddress(personalStakingAmount.getUserAddress());
                    gridStakingDetail.setStakingAmount(personalStakingAmount.getStakingAmount());
                    gridStakingDetail.setStakingQuota(new BigDecimal(personalStakingAmount.getStakingAmount()).divide(new BigDecimal(totalStakingAmount.toString()), 4, BigDecimal.ROUND_HALF_UP).toString());
                    gridStakingDetail.setFeeRatio(currentFeeRate);
                    //gridStakingDetail.setStakingReward("0");
                    gridStakingDetails.add(gridStakingDetail);
                }
                gridStakingDetailRepository.saveAll(gridStakingDetails);
                GridStakeReward stakeReward = gridStakeRewardRepository.findByEpochAndTokenId(currentEpoch, tokenId);
                if (stakeReward != null){
                    GridStakeReward gridStakeReward = new GridStakeReward();
                    gridStakeReward.setEpoch(currentEpoch);
                    gridStakeReward.setTokenId(tokenId);
                    gridStakeReward.setStakingAmount(totalStakingAmount.toString());
                    gridStakeReward.setStakingNumber(personalStakingAmounts.size());
                    gridStakeReward.setCurrentFeeRatio(currentFeeRate);
                    gridStakeReward.setNextFeeRatio(nextFeeRate);
                    gridStakeReward.setStakingProvider(grid.getOwnerAddress());
                    gridStakeReward.setStakingReward("0");
                    gridStakeRewardRepository.save(gridStakeReward);
                }
            }
        } catch (Exception e){
            log.error("The generate Personal Current Epoch Valid StakeReward task fail, tokenId: {} ", tokenId, e);
        } finally {
            lock.unlock();
            platformTransactionManager.commit(status);
        }
    }

    public boolean checkScanBlockNumber(){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String currentEpochBlockNumberKey = "currentEpoch" + currentEpoch + "blockNumber";
        BigInteger blockNumber;
        Object object = redisService.get(currentEpochBlockNumberKey);
        if (ObjectUtils.isEmpty(object)){
            blockNumber = web3jUtils.getBlockNumber(0);
            redisService.set("currentEpoch" + currentEpoch + "blockNumber", blockNumber.toString(), 24, TimeUnit.HOURS);
        } else {
            blockNumber = new BigInteger(object.toString());
        }

        ContractOffset contractOffset = contractOffsetService.findByContractAddress("Delay100_BLOCK_CONTRACT_FLAG");
        return contractOffset.getBlockOffset().compareTo(blockNumber) > 0;
    }

    @NotNull
    private List<GridStakingDetail> pageHelper(int pageSize, int pageNum, String orderBy, String sorted, List<GridStakingDetail> stakeRewards) {
        Comparator<GridStakingDetail> comparator = null;
        if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingAmount()));
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingReward()));
        } else if ("stakingQuota".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingQuota()));
        }

        if (comparator != null) {
            if (DESC_SORT.equalsIgnoreCase(sorted)) {
                comparator = comparator.reversed();
            }
            stakeRewards.sort(comparator);
        }

        int endIndex = pageNum * pageSize;
        int size = stakeRewards.size();
        endIndex = Math.min(endIndex, size);
        return stakeRewards.subList((pageNum - 1) * pageSize, endIndex);
    }

    private String buildCacheKey(String prefix, String epoch, String tokenId, int pageSize, int pageNum, String orderBy, String sorted) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        keyBuilder.append(":epoch:").append(epoch);
        appendIfNotEmpty(keyBuilder, ":tokenId:", tokenId);
        appendIfNotEmpty(keyBuilder, ":pageSize:", pageSize);
        appendIfNotEmpty(keyBuilder, ":pageNum:", pageNum);
        appendIfNotEmpty(keyBuilder, ":orderBy:", orderBy);
        appendIfNotEmpty(keyBuilder, ":sorted:", sorted);
        return keyBuilder.toString();
    }

    private void appendIfNotEmpty(StringBuilder builder, String suffix, Object value) {
        if (ObjectUtils.isNotEmpty(value)) {
            builder.append(suffix).append(value);
        }
    }

    public Page<GridStakingDetail> findPage(String epoch, String tokenId, int pageSize, int pageNum, String orderBy, String sorted) {
        String currentEpoch = web3jUtils.getCurrentEpoch();

        String stakeRewardPageKey = buildCacheKey("gridStakingDetailPage", epoch, tokenId, pageSize, pageNum, orderBy, sorted);
        String stakeRewardPageCountKey = buildCacheKey("gridStakingDetailPageCount", epoch, tokenId, pageSize, pageNum, orderBy, sorted);

        List<GridStakingDetail> stakeRewards = new ArrayList<>();
        try {
            Object listValue = redisService.get(stakeRewardPageKey);
            Object countValue = redisService.get(stakeRewardPageCountKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                stakeRewards = JSONObject.parseArray(v, GridStakingDetail.class);
                if (!stakeRewards.isEmpty()){
                    return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("----------- gridStakingDetail find page redis read error: {}", e.getMessage());
        }

        String stakeRewardQueryKey = buildCacheKey("gridStakingDetailQueryLockKey", epoch, tokenId, pageSize, pageNum, orderBy, sorted);
        if (!redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 30, TimeUnit.SECONDS )) {
            throw new RuntimeException("The system is busy, please try again later");
        }

        List<GridStakingDetail> gridStakingDetails = gridStakingDetailRepository.findByEpochAndTokenId(epoch, tokenId);

        gridStakingDetails = pageHelper(pageSize, pageNum, orderBy, sorted, gridStakingDetails);

        GridStakeReward gridStakeReward = gridStakeRewardRepository.findByEpochAndTokenId(epoch, tokenId);
        if (gridStakeReward == null){
            throw new RuntimeException("The grid is not found");
        }
        if (epoch.equals(currentEpoch)){
            String stakingReward = gridStakeReward.getStakingReward();
            String currentFeeRatio = gridStakeReward.getCurrentFeeRatio();
            for (GridStakingDetail gridStakingDetail : gridStakingDetails) {
                String stakingQuota = gridStakingDetail.getStakingQuota();
                if (!StringUtils.isEmpty(stakingQuota)){
                    gridStakingDetail.setStakingReward(
                            new BigDecimal(stakingReward).multiply(new BigDecimal("10000").subtract(new BigDecimal(currentFeeRatio)))
                                    .multiply(new BigDecimal(stakingQuota)).divide(new BigDecimal("10000"), 0, RoundingMode.HALF_UP).toString()
                    );
                }
            }
        }

        try {
            String pvoStr = JSON.toJSONString(gridStakingDetails, SerializerFeature.WriteNullStringAsEmpty);
            if (epoch.equalsIgnoreCase(currentEpoch)){
                redisService.set(stakeRewardPageKey, pvoStr, 5, TimeUnit.MINUTES);
                redisService.set(stakeRewardPageCountKey, String.valueOf(gridStakingDetails.size()), 15, TimeUnit.MINUTES);
            } else {
                redisService.set(stakeRewardPageKey, pvoStr, 24, TimeUnit.HOURS);
                redisService.set(stakeRewardPageCountKey, String.valueOf(gridStakingDetails.size()), 24, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("----------- Grid Staking Detail find page redis write error: {}", e.getMessage());
        }
        redisService.del(stakeRewardQueryKey);
        return new PageImpl<>(gridStakingDetails, PageRequest.of(pageNum - 1, pageSize), gridStakingDetails.size());
    }

    public List<GridStakingDetail> findByUseAddress(String userAddress) {
        return gridStakingDetailRepository.findByUserAddress(userAddress);
    }

    private BigInteger calculateStakingReward(String gridStakingReward, String feeRatio, String stakingQuota) {
        BigDecimal stakingReward = new BigDecimal(gridStakingReward);
        return stakingReward.multiply(new BigDecimal(feeRatio)).multiply(new BigDecimal(stakingQuota)).divide(new BigDecimal(10000)).setScale(0, RoundingMode.DOWN).toBigInteger();
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void generatePreviousEpochStakeDetailTask(){
        RLock previousEpochStakeDetailTaskLock = redissonClient.getLock("previousEpochStakeDetailTaskLock");

        try{
            if (previousEpochStakeDetailTaskLock.tryLock()){
                log.info("the generate previous epoch stake detail task is beginning");
                String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
                if (Integer.parseInt(previousEpoch) < 1) {
                    return;
                }

                if (!checkScanBlockNumber()){
                    log.info("Waiting for scanning block");
                    return;
                }

                List<GridStakeReward> stakeRewards = gridStakeRewardRepository.findAllByEpoch(previousEpoch);
                ExecutorService executorService = Executors.newFixedThreadPool((Math.min(stakeRewards.size(), 10)));

                for (GridStakeReward gridStakeReward : stakeRewards) {
                    executorService.execute(() -> updatePreviousEpochStakingReward(previousEpoch, gridStakeReward));
                }

                executorService.shutdown();
                try {
                    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error( "the generate previous epoch stake detail task fail: ", e);
                }
                log.info("the generate previous epoch stake detail task is finish");
            } else {
                log.info("the generate previous epoch stake detail task is already in progress");
            }
        }catch (Exception e){
            log.error("the generate previous epoch stake detail task fail:", e);
        }finally {
            previousEpochStakeDetailTaskLock.unlock();
        }
    }

    public void updatePreviousEpochStakingReward(String epoch, GridStakeReward gridStakeReward) {
        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        RLock lock = redissonClient.getLock("updatePreviousEpochStakingReward" + epoch + gridStakeReward.getTokenId());
        try {
            if (lock.tryLock()){
                String tokenId = gridStakeReward.getTokenId();
                String gridStakingReward = gridStakeReward.getStakingReward();
                List<GridStakingDetail> gridStakingDetails = gridStakingDetailRepository.findByEpochAndTokenId(epoch, tokenId);
                if (!gridStakingDetails.isEmpty()){
                    if (gridStakingDetails.get(0).isValid()) {
                        return;
                    }
                }
                for (GridStakingDetail gridStakingDetail : gridStakingDetails) {
                    gridStakingDetail.setStakingReward(calcPersonalStakingReward(gridStakingReward, gridStakeReward.getCurrentFeeRatio(), gridStakingDetail.getStakingQuota()));
                    gridStakingDetail.setValid(true);
                }
                gridStakingDetailRepository.saveAll(gridStakingDetails);
            }
        } catch (Exception e){
            platformTransactionManager.rollback(status);
            log.error("the generate previous epoch stake detail task fail:", e);
        } finally {
            lock.unlock();
            platformTransactionManager.commit(status);
        }
    }

    private String calcPersonalStakingReward(String gridStakingReward, String feeRatio, String stakingQuota) {
        return (new BigInteger(gridStakingReward).subtract(new BigInteger(gridStakingReward).multiply(new BigInteger(feeRatio).subtract(new BigInteger("10000"))))).multiply(new BigInteger(stakingQuota)).toString();
    }

    public Map<String, String> findRewardData(String userAddress, String epoch, String tokenId) {
        String accumulatedRewardCacheKey = "findRewardData:accumulatedReward:" + userAddress + ":" + epoch + ":" + tokenId;
        String claimableRewardCacheKey = "findRewardData:claimableReward:" + userAddress + ":" + epoch + ":" + tokenId;
        try {
            Object accumulatedRewardRedisValue = redisService.get(accumulatedRewardCacheKey);
            Object claimableRewardRedisValue = redisService.get(claimableRewardCacheKey);
            if (null != accumulatedRewardRedisValue && null != claimableRewardRedisValue) {
                String accumulatedReward = accumulatedRewardRedisValue.toString();
                String claimableReward = claimableRewardRedisValue.toString();
                return new HashMap<String, String>() {{
                    put("accumulatedReward", accumulatedReward);
                    put("claimableReward", claimableReward);
                }};
            }
        }catch (Exception e){
            log.error("findRewardData redis read error：{}", e.getMessage());
        }
        Map<String, String> result = new HashMap<>();
        List<GridStakingDetail> details = gridStakingDetailRepository.findAllByUserAddressAndEpochLessThanAndTokenIdOrderByCreateTimeDesc(userAddress, epoch, tokenId);
        BigInteger accumulatedReward = BigInteger.ZERO;
        for (GridStakingDetail detail : details) {
            accumulatedReward = accumulatedReward.add(new BigInteger(detail.getStakingReward()));
        }
        result.put("accumulatedReward", accumulatedReward.toString());
        List<NodePoolEvents> claimReward = nodePoolEventsRepository.findAllByUserAndTokenIdAndEventAndEpochLessThanEqual(userAddress, tokenId, "CLAIM_REWARD", epoch);
        BigInteger claimedReward = claimReward.stream().map(NodePoolEvents::getAmount).map(BigInteger::new).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger claimableReward = accumulatedReward.subtract(claimedReward);
        result.put("claimableReward", claimableReward.toString());
        try {
            redisService.set(accumulatedRewardCacheKey, accumulatedReward.toString(), 10, TimeUnit.SECONDS);
            redisService.set(claimableRewardCacheKey, claimableReward.toString(), 10, TimeUnit.SECONDS);
        }catch (Exception e){
            log.error("StakeRewardOverview findLastEpoch redis write error：{}", e.getMessage());
        }
        return result;
    }
}
