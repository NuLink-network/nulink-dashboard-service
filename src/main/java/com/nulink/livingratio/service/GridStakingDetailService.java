package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.contract.task.listener.Tasks;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.entity.event.Bond;
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

    private static final Object generatePersonalCurrentEpochStakeRewardTaskKey = new Object();
    private static boolean generatePersonalCurrentEpochStakeRewardTaskTaskFlag = false;

    private static final Object generatePersonalPreviousEpochStakeRewardTaskKey = new Object();
    private static boolean generatePersonalPreviousEpochStakeRewardTaskTaskFlag = false;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    private final GridStakingDetailRepository gridStakingDetailRepository;

    private final GridStakeRewardRepository gridStakeRewardRepository;

    private final ContractOffsetService contractOffsetService;

    private final EpochFeeRateEventService epochFeeRateEventService;

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    private final CreateNodePoolEventService createNodePoolEventService;

    private final BondRepository bondRepository;

    private final RedisService redisService;

    private final Web3jUtils web3jUtils;
    @Autowired
    private NodePoolEventsRepository nodePoolEventsRepository;

    public GridStakingDetailService(GridStakingDetailRepository gridStakingDetailRepository,
                                    GridStakeRewardRepository gridStakeRewardRepository,
                                    ContractOffsetService contractOffsetService,
                                    EpochFeeRateEventService epochFeeRateEventService,
                                    ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository,
                                    CreateNodePoolEventService createNodePoolEventService, BondRepository bondRepository,
                                    RedisService redisService,
                                    Web3jUtils web3jUtils) {
        this.gridStakingDetailRepository = gridStakingDetailRepository;
        this.gridStakeRewardRepository = gridStakeRewardRepository;
        this.contractOffsetService = contractOffsetService;
        this.epochFeeRateEventService = epochFeeRateEventService;
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
        this.createNodePoolEventService = createNodePoolEventService;
        this.bondRepository = bondRepository;
        this.redisService = redisService;
        this.web3jUtils = web3jUtils;
    }

    public void create(GridStakingDetail gridStakingDetail) {
        gridStakingDetailRepository.save(gridStakingDetail);
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void generatePersonalCurrentEpochStakeRewardTask(){
        synchronized (generatePersonalCurrentEpochStakeRewardTaskKey) {
            if (GridStakingDetailService.generatePersonalCurrentEpochStakeRewardTaskTaskFlag) {
                log.warn("The generatePersonalCurrentEpochStakeRewardTask task is already in progress");
                return;
            }
            GridStakingDetailService.generatePersonalCurrentEpochStakeRewardTaskTaskFlag = true;
        }
        RLock personalCurrentEpochStakeRewardTaskLock = redissonClient.getLock("PersonalCurrentEpochStakeRewardTask");
        try{
            if (personalCurrentEpochStakeRewardTaskLock.tryLock()){
                log.info("The generate Personal Current Epoch Valid StakeReward task is beginning");
                String currentEpoch = web3jUtils.getCurrentEpoch();
                if (Integer.parseInt(currentEpoch) < 1) {
                    return;
                }

                if (!checkScanBlockNumber()){
                    log.info("Waiting for scanning block");
                    return;
                }

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
            if (personalCurrentEpochStakeRewardTaskLock.isLocked()){
                personalCurrentEpochStakeRewardTaskLock.unlock();
            }
            GridStakingDetailService.generatePersonalCurrentEpochStakeRewardTaskTaskFlag = false;
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
                List<GridStakingDetail> stakingDetails = gridStakingDetailRepository.findByEpochAndTokenId(currentEpoch, tokenId);
                if (!stakingDetails.isEmpty()){
                    log.info("Grid {} Current Epoch Valid StakeReward task has already been executed.", tokenId);
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
                BigInteger totalStakingAmount = BigInteger.ZERO;
                for (ValidPersonalStakingAmount personalStakingAmount : personalStakingAmounts) {
                    String stakingAmount = personalStakingAmount.getStakingAmount();
                    if (StringUtils.isNotBlank(stakingAmount)){
                        totalStakingAmount = totalStakingAmount.add(new BigInteger(stakingAmount));
                    }
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
                    gridStakingDetail.setStakingReward("0");
                    gridStakingDetails.add(gridStakingDetail);
                }
                gridStakingDetailRepository.saveAll(gridStakingDetails);
                GridStakeReward stakeReward = gridStakeRewardRepository.findByEpochAndTokenId(currentEpoch, tokenId);
                if (stakeReward == null){
                    GridStakeReward gridStakeReward = new GridStakeReward();
                    gridStakeReward.setEpoch(currentEpoch);
                    gridStakeReward.setTokenId(tokenId);
                    gridStakeReward.setStakingAmount(totalStakingAmount.toString());
                    gridStakeReward.setStakingNumber(personalStakingAmounts.size());
                    gridStakeReward.setCurrentFeeRatio(currentFeeRate);
                    gridStakeReward.setNextFeeRatio(nextFeeRate);
                    gridStakeReward.setStakingProvider(grid.getOwnerAddress());
                    gridStakeReward.setGridAddress(grid.getNodePoolAddress());
                    gridStakeReward.setStakingReward("0");
                    Bond bond = bondRepository.findFirstByStakingProviderOrderByCreateTimeDesc(grid.getNodePoolAddress());
                    if (bond != null && !bond.getOperator().equals("0x0000000000000000000000000000000000000000")) {
                        gridStakeReward.setOperator(bond.getOperator());
                    }
                    gridStakeRewardRepository.save(gridStakeReward);
                }
            }
        } catch (Exception e){
            log.error("The generate Personal Current Epoch Valid StakeReward task fail, tokenId: {} ", tokenId, e);
            platformTransactionManager.rollback(status);
        } finally {
            if (lock.isLocked()){
                lock.unlock();
            }
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

        BigInteger blockOffset = contractOffsetService.findMinBlockOffset();
        return blockOffset.compareTo(blockNumber) > 0;
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

        List<GridStakingDetail> stakeRewards;
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
            redisService.del(stakeRewardQueryKey);
            throw new RuntimeException("The grid is not found");
        }
        String stakingReward = gridStakeReward.getStakingReward();
        String currentFeeRatio = gridStakeReward.getCurrentFeeRatio();
        BigDecimal fee = new BigDecimal(stakingReward).multiply(new BigDecimal(currentFeeRatio)).divide(new BigDecimal("10000"), 0, RoundingMode.HALF_UP);
        if (epoch.equals(currentEpoch)){
            for (GridStakingDetail gridStakingDetail : gridStakingDetails) {
                String stakingQuota = gridStakingDetail.getStakingQuota();
                if (!StringUtils.isEmpty(stakingQuota)){
                    gridStakingDetail.setFee(
                            fee.multiply(new BigDecimal(stakingQuota)).setScale(0, RoundingMode.HALF_UP).toString()
                    );
                    gridStakingDetail.setStakingReward((new BigDecimal(stakingReward).subtract(fee)).multiply(new BigDecimal(stakingQuota)).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
        } else {
            for (GridStakingDetail gridStakingDetail : gridStakingDetails) {
                gridStakingDetail.setFee(fee.multiply(new BigDecimal(gridStakingDetail.getStakingQuota())).setScale(0, RoundingMode.HALF_UP).toString());
            }
        }
        for (GridStakingDetail gridStakingDetail : gridStakingDetails) {
            gridStakingDetail.setIndex((pageNum - 1) * pageSize + gridStakingDetails.indexOf(gridStakingDetail) + 1);
        }

        try {
            String pvoStr = JSON.toJSONString(gridStakingDetails, SerializerFeature.WriteNullStringAsEmpty);
            if (epoch.equalsIgnoreCase(currentEpoch)){
                redisService.set(stakeRewardPageKey, pvoStr, 20, TimeUnit.SECONDS);
                redisService.set(stakeRewardPageCountKey, String.valueOf(gridStakingDetails.size()), 20, TimeUnit.SECONDS);
            } else {
                redisService.set(stakeRewardPageKey, pvoStr, 5, TimeUnit.MINUTES);
                redisService.set(stakeRewardPageCountKey, String.valueOf(gridStakingDetails.size()), 5, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            redisService.del(stakeRewardQueryKey);
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
        synchronized (generatePersonalPreviousEpochStakeRewardTaskKey) {
            if (GridStakingDetailService.generatePersonalPreviousEpochStakeRewardTaskTaskFlag) {
                log.warn("The generatePreviousEpochStakeDetailTask task is already in progress");
                return;
            }
            GridStakingDetailService.generatePersonalPreviousEpochStakeRewardTaskTaskFlag = true;
        }
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
                if (stakeRewards.isEmpty()){
                    return;
                }
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
            if (previousEpochStakeDetailTaskLock.isLocked()){
                previousEpochStakeDetailTaskLock.unlock();
            }
            GridStakingDetailService.generatePersonalPreviousEpochStakeRewardTaskTaskFlag = false;
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
            platformTransactionManager.commit(status);
            if (lock.isLocked()){
                lock.unlock();
            }
        }
    }

    private String calcPersonalStakingReward(String gridStakingReward, String feeRatio, String stakingQuota) {
        return (new BigDecimal(gridStakingReward).subtract(
                new BigDecimal(gridStakingReward).multiply(new BigDecimal(feeRatio).divide(new BigDecimal("10000")))))
                .multiply(new BigDecimal(stakingQuota)).setScale(0, RoundingMode.HALF_UP).toString();
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
