package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.repository.*;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
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
import javax.persistence.criteria.Predicate;
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

    private GridStakingDetailRepository gridStakingDetailRepository;

    private final GridStakeRewardRepository gridStakeRewardRepository;

    private final ContractOffsetService contractOffsetService;

    private final EpochFeeRateEventService epochFeeRateEventService;

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    private final CreateNodePoolEventService createNodePoolEventService;

    private final RedisService redisService;

    private final Web3jUtils web3jUtils;

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
                if (Integer.parseInt(currentEpoch) < 1){
                    personalCurrentEpochStakeRewardTaskLock.unlock();
                    return;
                }

                if (!checkScanBlockNumber()){
                    log.info("Waiting for scanning block");
                    personalCurrentEpochStakeRewardTaskLock.unlock();
                    return;
                }

                List<CreateNodePoolEvent> nodePools = createNodePoolEventService.findAll();
                ExecutorService executorService = Executors.newFixedThreadPool((Math.min(nodePools.size(), 10)));

                for (CreateNodePoolEvent pool : nodePools) {
                    executorService.execute(() -> generatePersonalCurrentEpochValidStakeReward(pool, currentEpoch));
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
        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        List<GridStakingDetail> stakingDetails = gridStakingDetailRepository.findByEpochAndTokenId(tokenId, currentEpoch);
        if (!stakingDetails.isEmpty()){
            log.info("Grid {} Current Epoch Valid StakeReward task has already been executed.", tokenId);
            platformTransactionManager.commit(status);
            return;
        }
        // Get the valid personal staking amount
        List<ValidPersonalStakingAmount> personalStakingAmounts = validPersonalStakingAmountRepository.findAllByTokenIdAndEpochLessThanEqual(tokenId, Integer.parseInt(currentEpoch));
        if (personalStakingAmounts.isEmpty()){
            log.info("Grid {} valid personal staking amount is empty", tokenId);
            platformTransactionManager.commit(status);
            return;
        }
        try {
            String currentFeeRate = epochFeeRateEventService.getCurrentFeeRate(tokenId);
            String nextFeeRate = epochFeeRateEventService.getNextFeeRate(tokenId);
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
                gridStakingDetail.setStakingProvider(personalStakingAmount.getUserAddress());
                gridStakingDetail.setStakingAmount(personalStakingAmount.getStakingAmount());
                gridStakingDetail.setStakingQuota(new BigDecimal(personalStakingAmount.getStakingAmount()).divide(new BigDecimal(totalStakingAmount.toString()), 4, BigDecimal.ROUND_HALF_UP).toString());
                gridStakingDetail.setFeeRatio(currentFeeRate);
                gridStakingDetail.setStakingReward("0");
                gridStakingDetails.add(gridStakingDetail);
            }
            gridStakingDetailRepository.saveAll(gridStakingDetails);
            GridStakeReward gridStakeReward = new GridStakeReward();
            gridStakeReward.setEpoch(currentEpoch);
            gridStakeReward.setTokenId(tokenId);
            gridStakeReward.setValidStakingAmount(totalStakingAmount.toString());
            gridStakeReward.setStakingNumber(personalStakingAmounts.size());
            gridStakeReward.setCurrentFeeRatio(currentFeeRate);
            gridStakeReward.setNextFeeRatio(nextFeeRate);
            gridStakeReward.setStakingProvider(grid.getOwnerAddress());
            gridStakeRewardRepository.save(gridStakeReward);
        } catch (Exception e){
            log.error("The generate Personal Current Epoch Valid StakeReward task fail, tokenId: {} ", tokenId, e);
        } finally {
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

        ContractOffset contractOffset = contractOffsetService.findByContractAddress("Delay30_BLOCK_CONTRACT_FLAG");
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

        try {
            String pvoStr = JSON.toJSONString(gridStakingDetails, SerializerFeature.WriteNullStringAsEmpty);
            if (epoch.equalsIgnoreCase(currentEpoch)){
                redisService.set(stakeRewardPageKey, pvoStr, 15, TimeUnit.MINUTES);
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

    private Sort resolveSort(String orderBy, String sorted) {
        Sort sort;
        if ("stakingReward".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingReward");
        } else if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingAmount");
        } else if ("stakingQuota".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingQuota");
        } else {
            sort = Sort.by("createTime");
        }
        return "DESC".equalsIgnoreCase(sorted) ? sort.descending() : sort.ascending();
    }

}
