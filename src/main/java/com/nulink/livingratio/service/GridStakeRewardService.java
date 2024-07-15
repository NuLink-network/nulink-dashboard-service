package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.entity.event.Bond;
import com.nulink.livingratio.entity.event.StakingEvent;
import com.nulink.livingratio.repository.BondRepository;
import com.nulink.livingratio.repository.PersonalStakeRepository;
import com.nulink.livingratio.repository.GridStakeRewardRepository;
import com.nulink.livingratio.utils.HttpClientUtil;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.PorterRequestVO;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class GridStakeRewardService {

    private static final Object countPreviousEpochStakeRewardTaskKey = new Object();
    private static boolean lockCountPreviousEpochStakeRewardTaskFlag = false;

    private static final Object generateCurrentEpochValidStakeRewardTaskKey = new Object();
    private static boolean generateCurrentEpochValidStakeRewardTaskFlag = false;

    private static final Object livingRatioTaskKey = new Object();
    private static boolean livingRatioTaskFlag = false;

    private final static String STAKE_EVENT = "stake";

    private final static String UN_STAKE_EVENT = "unstake";

    private final static String PING = "/ping";

    private static final String DESC_SORT = "desc";
    private static final String ASC_SORT = "asc";

    private final String INCLUDE_URSULA = "/include/ursulas";
    private final String CHECK_URSULA_API = "/check/ursula";
    private static String CHECK_RUNNING = "/check-running";

    @Value("${NULink.porter-service-url}")
    private String porterServiceUrl;

    private final GridStakeRewardRepository stakeRewardRepository;
    private final PersonalStakeRepository stakeRepository;
    private final BondRepository bondRepository;
    private final Web3jUtils web3jUtils;
    private final IncludeUrsulaService includeUrsulaService;

    private final RedisService redisService;

    private final ValidStakingAmountService validStakingAmountService;

    private final ContractOffsetService contractOffsetService;
    @Resource
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private StakeRewardOverviewService stakingRewardOverviewService;

    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    public GridStakeRewardService(GridStakeRewardRepository stakeRewardRepository,
                                  PersonalStakeRepository stakeRepository,
                                  BondRepository bondRepository,
                                  Web3jUtils web3jUtils,
                                  IncludeUrsulaService includeUrsulaService, RedisService redisService,
                                  ValidStakingAmountService validStakingAmountService,
                                  ContractOffsetService contractOffsetService, RedisTemplate<String, String> redisTemplate) {
        this.stakeRewardRepository = stakeRewardRepository;
        this.stakeRepository = stakeRepository;
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
        this.includeUrsulaService = includeUrsulaService;
        this.redisService = redisService;
        this.validStakingAmountService = validStakingAmountService;
        this.contractOffsetService = contractOffsetService;
        this.redisTemplate = redisTemplate;
    }

    // When the epoch starts, generate the list of stake rewards for the previous epoch
    /*@Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void generateCurrentEpochValidStakeReward(){
        synchronized (generateCurrentEpochValidStakeRewardTaskKey) {
            if (GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag) {
                log.warn("The generate Current Epoch Valid StakeReward task is already in progress");
                return;
            }
            GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = true;
        }

        log.info("The generate Current Epoch Valid StakeReward task is beginning");

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            String currentEpoch = web3jUtils.getCurrentEpoch();
            if (Integer.valueOf(currentEpoch) < 1){
                GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            List<GridStakeReward> stakeRewardList = stakeRewardRepository.findAllByEpoch(currentEpoch);
            if (!stakeRewardList.isEmpty()){
                GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                log.info("The Current Epoch Valid StakeReward task has already been executed.");
                platformTransactionManager.commit(status);
                return;
            }
            if (!checkScanBlockNumber()){
                GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                log.info("Waiting for scanning block");
                platformTransactionManager.commit(status);
                return;
            }
            List<PersonalStaking> validStake = stakeService.findValidStakeByEpoch(currentEpoch);
            if (validStake.isEmpty()){
                GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            validStake = validStake.stream().filter(stake -> !stake.getAmount().equals("0")).collect(Collectors.toList());
            List<Bond> bounds = bondRepository.findLatestBond();
            HashMap<String, String> bondMap  = new HashMap<>();
            bounds.forEach(bond -> bondMap.put(bond.getStakingProvider(), bond.getOperator()));
            List<String> stakingAddress = validStake.stream().map(PersonalStaking::getUser).collect(Collectors.toList());

            List<String> nodeAddresses = findNodeAddress(stakingAddress);

            List<GridStakeReward> stakeRewards = new ArrayList<>();

            Map<String, String> validStakingAmount = validStakingAmountService.findValidStakingAmount(Integer.parseInt(currentEpoch) - 1);

            validStake.forEach(stake -> {
                GridStakeReward stakeReward = new GridStakeReward();
                stakeReward.setEpoch(currentEpoch);
                String stakeUser = stake.getUser();
                if (bondMap.get(stakeUser) != null) {
                    stakeReward.setOperator(bondMap.get(stakeUser));
                    String url = nodeAddresses.get(stakingAddress.indexOf(stakeUser));
                    if (StringUtils.isNotEmpty(url)){
                        stakeReward.setIpAddress(getIpAddress(url));
                    }
                }
                stakeReward.setTokenId(stakeUser);
                stakeReward.setStakingAmount(validStakingAmount.get(stakeUser) == null?"0":validStakingAmount.get(stakeUser));
                stakeReward.setValidStakingAmount("0");
                stakeReward.setLivingRatio("0");
                stakeRewards.add(stakeReward);
            });
            stakeRewards.removeIf(stakeReward -> stakeReward.getStakingAmount().equals("0"));
            stakeRewardRepository.saveAll(stakeRewards);
            StakeRewardOverview stakeRewardOverview = stakingRewardOverviewService.getStakeRewardOverview(stakeRewards, currentEpoch);
            stakingRewardOverviewService.saveByEpoch(stakeRewardOverview);
            platformTransactionManager.commit(status);
            GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
            log.info("The generate Current Epoch Valid StakeReward task is finish");
        }catch (Exception e){
            platformTransactionManager.rollback(status);
            log.error("The generate Current Epoch Valid StakeReward task fail:" + e);
            GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
        }finally {
            GridStakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
        }
    }*/

    @Async
    @Scheduled(cron = "0 0 * * * ?")
    //@Scheduled(cron = "0 0/5 * * * ? ")
    public void livingRatio() {
        RLock fairLock = redissonClient.getFairLock("livingRatioTaskKey");

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            if (fairLock.tryLock()) {

                String epoch = web3jUtils.getCurrentEpoch();
                if (Integer.parseInt(epoch) < 1){
                    GridStakeRewardService.livingRatioTaskFlag = false;
                    return;
                }
                log.info("living ratio task start ...........................");
                List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);


                List<String> stakingAddress = stakeRewards.stream().map(GridStakeReward::getStakingProvider).collect(Collectors.toList());
                List<String> nodeAddresses = findNodeAddress(stakingAddress);

                CheckNodeExecutor checkNodeExecutor = new CheckNodeExecutor();
                List<CheckNodeExecutor.ServerStatus> serverStatuses = new ArrayList<>();
                for (int i = 0; i < stakingAddress.size(); i++) {
                    String address = nodeAddresses.get(i);
                    if(StringUtils.isNotEmpty(address)){
                        serverStatuses.add(new CheckNodeExecutor.ServerStatus(address, stakingAddress.get(i)));
                    }
                }
                List<CheckNodeExecutor.ServerStatus> serverStatusesResult = checkNodeExecutor.executePingTasks(serverStatuses);
                Map<String, Boolean> nodeCheckMap = new HashMap<>();
                serverStatusesResult.forEach(serverStatus -> nodeCheckMap.put(serverStatus.getStakingProvider(), serverStatus.isOnline()));
                checkNodeExecutor.shutdown(); // check node finish , executor shutdown

                int connectable = 0;

                for (GridStakeReward stakeReward : stakeRewards) {
                    stakeReward.setPingCount(stakeReward.getPingCount() + 1);
                    String stakingProvider = stakeReward.getTokenId();

                    if (isUnBond(stakingProvider)){
                        stakeReward.setUnStake(stakeReward.getUnStake() + 1);
                    } else {
                        String nodeAddress  = nodeAddresses.get(stakingAddress.indexOf(stakingProvider));
                        if (StringUtils.isNotEmpty(nodeAddress)){
                            String ipAddress = getIpAddress(nodeAddress);
                            if (StringUtils.isEmpty(stakeReward.getIpAddress())){
                                stakeReward.setIpAddress(ipAddress);
                            } else {
                                if (!stakeReward.getIpAddress().equals(ipAddress)){
                                    stakeReward.setIpAddress(ipAddress);
                                }
                            }
                            Boolean b = nodeCheckMap.get(stakeReward.getTokenId());
                            if (b != null && b){
                                stakeReward.setConnectable(stakeReward.getConnectable() + 1);
                                connectable ++;
                            } else {
                                stakeReward.setConnectFail(stakeReward.getConnectFail() + 1);
                            }
                        } else {
                            stakeReward.setConnectFail(stakeReward.getConnectFail() + 1);
                        }
                    }
                    stakeReward.setLivingRatio(new BigDecimal(stakeReward.getConnectable()).divide(new BigDecimal(stakeReward.getPingCount()), 4, RoundingMode.HALF_UP).toString());
                }
                stakeRewardRepository.saveAll(stakeRewards);
                includeUrsulaService.setIncludeUrsula(connectable);
                StakeRewardOverview stakeRewardOverview = stakingRewardOverviewService.getStakeRewardOverview(stakeRewards, epoch);
                stakingRewardOverviewService.saveByEpoch(stakeRewardOverview);
                platformTransactionManager.commit(status);
                log.info("living ratio task finish ...........................");
            }
        }catch (Exception e){
            log.error("The living Ratio task is failed");
            platformTransactionManager.rollback(status);
            throw new RuntimeException(e);
        }finally {
            fairLock.unlock();
        }
    }

    private boolean isUnBond(String stakingProvider){
        Bond lastBond = bondRepository.findFirstByStakingProviderOrderByCreateTimeDesc(stakingProvider);
        if (lastBond != null){
            return lastBond.getOperator().equals("0x0000000000000000000000000000000000000000");
        } else {
            return true;
        }
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void countPreviousEpochStakeReward(){

        synchronized (countPreviousEpochStakeRewardTaskKey) {
            if (GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag) {
                log.warn("The count Previous Epoch Stake Reward task is already in progress");
                return;
            }
            GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = true;
        }

        log.info("The count Previous Epoch Stake Reward task is beginning");

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
            List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(previousEpoch);
            if (stakeRewards.isEmpty()){
                GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            if (null == stakeRewards.get(0).getStakingReward()){
                countStakeReward(stakeRewards, previousEpoch);
                stakeRewardRepository.saveAll(stakeRewards);
            } else {
                log.info("The count Previous Epoch Stake Reward task has already been executed.");
            }
            platformTransactionManager.commit(status);
            GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
            log.info("The count Previous Epoch Stake Reward task is finish");
        } catch (Exception e){
            log.error("The count Previous Epoch Stake Reward task is fail", e);
            platformTransactionManager.rollback(status);
            GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
        } finally {
            GridStakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
        }
    }

    public void countStakeReward(List<GridStakeReward> stakeRewards, String epoch){
        if (!stakeRewards.isEmpty()){
            for (GridStakeReward stakeReward : stakeRewards) {
                if (StringUtils.isNotEmpty(stakeReward.getLivingRatio())){
                    stakeReward.setValidStakingAmount(new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
            String totalValidStakingAmount = sum(stakeRewards.stream().map(GridStakeReward::getValidStakingAmount).collect(Collectors.toList()));
            String currentEpochReward = web3jUtils.getEpochReward(epoch);
            for (GridStakeReward stakeReward : stakeRewards) {
                if (new BigDecimal(totalValidStakingAmount).compareTo(BigDecimal.ZERO) > 0){
                    String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(totalValidStakingAmount),6, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingQuota(validStakingQuota);
                    stakeReward.setStakingReward(new BigDecimal(validStakingQuota).multiply(new BigDecimal(currentEpochReward)).setScale(0, RoundingMode.HALF_UP).toString());
                } else {
                    stakeReward.setValidStakingQuota("0");
                    stakeReward.setStakingReward("0");
                }
            }
        }
    }

    private String sum(List<String> amount){
        BigDecimal sum = BigDecimal.ZERO;
        for (String s : amount) {
            sum = sum.add(new BigDecimal(s));
        }
        return sum.toString();
    }

    private String getStakingAmount(String stakingAddress, String epochTime){
        StakingEvent unStake = stakeRepository.findFirstByUserAndEventAndCreateTimeBeforeOrderByCreateTimeDesc(stakingAddress, UN_STAKE_EVENT, new Timestamp(Long.parseLong(epochTime) * 1000));
        List<StakingEvent> stakes;
        if (unStake != null) {
            Timestamp unStakeCreateTime = unStake.getCreateTime();
            stakes = stakeRepository.findAllByUserAndEventAndCreateTimeBetween(stakingAddress, STAKE_EVENT, unStakeCreateTime, new Timestamp(Long.parseLong(epochTime) * 1000));
        } else {
            stakes = stakeRepository.findAllByUser(stakingAddress);
        }
        if (stakes.isEmpty()){
            return BigDecimal.ZERO.toString();
        }
        List<BigDecimal> amounts = stakes.stream().map(stake -> new BigDecimal(stake.getAmount())).collect(Collectors.toList());
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).toString();
    }

    private String getIpAddress(String url){
        if (null == url){
            return null;
        }
        return url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(":"));
    }

    /**
     * findNodeAddress
     * @param stakingAddress
     * @return
     */
    public List<String> findNodeAddress(List<String> stakingAddress) throws IOException{

        List<String> result = new ArrayList<>();
        int batchSize = 500;
        int totalElements = stakingAddress.size();
        int batches = (int) Math.ceil((double) totalElements / batchSize);

        OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        MediaType mediaType = MediaType.parse("application/json");
        String url = porterServiceUrl + INCLUDE_URSULA;
        for (int i = 0; i < batches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min((i + 1) * batchSize, totalElements);
            List<String> batchList = stakingAddress.subList(fromIndex, toIndex);

            Map<String, List<String>> requestMap = new HashMap<>();
            requestMap.put("include_ursulas", batchList);
            String requestJson = objectMapper.writeValueAsString(requestMap);
            RequestBody requestBody = RequestBody.create(mediaType, requestJson);
            Request request = new Request.Builder().url(url).post(requestBody).build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                PorterRequestVO porterRequestVO = objectMapper.readValue(response.body().string(), PorterRequestVO.class);
                result.addAll(porterRequestVO.getResult().getList());
            } else {
                log.error("Failed to fetch work address for batch: " + i);
            }
            response.close();
        }
        return result;
    }

    public boolean pingNode(String nodeAddress) {
        OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
        Request request = new Request.Builder()
                .url(nodeAddress + PING)
                .build();
        Call call = client.newCall(request);

        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            log.error("Node connect failure:" + nodeAddress);
            throw new RuntimeException(e.getMessage());
        }
        if (response.isSuccessful()) {
            response.close();
            return true;
        } else {
            log.error("Request failed. Response code: " + response.code());
            response.close();
            return false;
        }
    }

    public boolean checkNode(String stakeAddress) throws IOException {
        try{
            OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse(porterServiceUrl + CHECK_URSULA_API).newBuilder();
            urlBuilder.addQueryParameter("staker_address", stakeAddress);
            String url = urlBuilder.build().toString();
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                response.body().close();
                return jsonNode.has("result") && jsonNode.get("result").has("data");
            } else {
                log.error("check ursula failed. Response code: " + response.code());
                return false;
            }
        } catch (Exception e){
            log.error("check ursula failed. -" + stakeAddress + "-" + e.getMessage());
            return false;
        }
    }

    public GridStakeReward nodeInfo(String stakingProvider){
        StakingEvent stake = stakeRepository.findFirstByUserAndEventOrderByCreateTimeDesc(stakingProvider, STAKE_EVENT);
        if (null == stake){
            return null;
        }
        GridStakeReward stakeReward = new GridStakeReward();
        stakeReward.setTokenId(stake.getUser());
        Bond bond = bondRepository.findLastOneBondByStakingProvider(stakingProvider);
        if (null != bond){
            stakeReward.setOperator(bond.getOperator());
            List<String> nodeAddress = null;
            try {
                nodeAddress = findNodeAddress(Collections.singletonList(stake.getUser()));
            } catch (IOException e) {
                log.error("fetch node url in porter failed:" + e.getMessage());
                throw new RuntimeException(e);
            }
            String nodeUrl = nodeAddress.get(0);
            if (StringUtils.isNotEmpty(nodeUrl)){
                String ipAddress = getIpAddress(nodeUrl);
                stakeReward.setIpAddress(ipAddress);
                try {
                    stakeReward.setOnline(checkNode(stakingProvider));
                } catch (IOException e) {
                    log.error("check node status failed:" + e.getMessage());
                    throw new RuntimeException(e);
                }
            } else {
                /*StakeReward s = stakeRewardRepository.findFirstByStakingProviderAndIpAddressIsNotNullOrderByCreateTimeDesc(stakingProvider);
                if (null != s){
                    stakeReward.setIpAddress(s.getIpAddress());
                }*/
                stakeReward.setOnline(false);
            }
        } else {
            stakeReward.setOnline(false);
        }
        return stakeReward;
    }

    public GridStakeReward findByEpochAndStakingProvider(String stakingProvider, String epoch){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (currentEpoch.equals(epoch)){
            List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);
            countStakeReward(stakeRewards, epoch);
            for (GridStakeReward stakeReward : stakeRewards) {
                if (stakeReward.getStakingProvider().equals(stakingProvider)){
                    return stakeReward;
                }
            }
        } else {
            return stakeRewardRepository.findByEpochAndStakingProvider(epoch, stakingProvider);
        }
        return null;
    }

    public GridStakeReward findByEpochAndTokenId(String tokenId, String epoch){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (currentEpoch.equals(epoch)){
            List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);
            countStakeReward(stakeRewards, epoch);
            for (GridStakeReward stakeReward : stakeRewards) {
                if (stakeReward.getTokenId().equals(tokenId)){
                    return stakeReward;
                }
            }
        } else {
            return stakeRewardRepository.findByEpochAndTokenId(epoch, tokenId);
        }
        return null;
    }

    @Async
    @Scheduled(cron = "0 0/10 * * * ? ")
    public void renewalFindPageCache() {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        renewalFindPageCache(currentEpoch, 10, 1, null, null);
    }

    public void deleteAllKeys() {
        Set<String> keys = redisTemplate.keys("*");

        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    public Page<GridStakeReward> renewalFindPageCache(String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        long startTime = System.currentTimeMillis();
        log.info("----------- renewalFindPageCache startTime:{}", startTime);
        // 构造Key的逻辑封装
        String stakeRewardPageKey = buildKey("stakeRewardPage", epoch, pageSize, pageNum, orderBy, sorted);
        String stakeRewardPageCountKey = buildKey("stakeRewardPageCount", epoch, pageSize, pageNum, orderBy, sorted);

        List<GridStakeReward> stakeRewards = new ArrayList<>();

        String stakeRewardQueryKey = buildKey("stakeRewardQueryKey", epoch, pageSize, pageNum, orderBy, sorted);
        if (!redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 30, TimeUnit.SECONDS )) {
            throw new RuntimeException("The system is busy, please try again later");
        }

        long redisLockEndTime = System.currentTimeMillis();
        log.info("----------- renewalFindPageCache Redis lock time: {}ms", redisLockEndTime - startTime);

        Sort sort = resolveSort(orderBy, sorted);
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);

        Specification<GridStakeReward> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(epoch)) {
                return criteriaBuilder.equal(root.get("epoch"), epoch);
            }
            return null;
        };

        Page<GridStakeReward> page = stakeRewardRepository.findAll(specification, pageable);
        List<GridStakeReward> content = page.getContent();
        StakeRewardOverview overview = stakingRewardOverviewService.findByEpoch(epoch);
        if (null != overview){
            String validStakingAmountTotal = overview.getValidStakingAmount();
            String currentEpochReward = overview.getCurrentEpochReward();
            if (!"0".equals(validStakingAmountTotal)){
                for (GridStakeReward stakeReward : content) {
                    String validStakingAmount = new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingAmount(validStakingAmount);
                    String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(validStakingAmountTotal),6, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingQuota(validStakingQuota);
                    stakeReward.setStakingReward(new BigDecimal(validStakingQuota).multiply(new BigDecimal(currentEpochReward)).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
        }
        stakeRewards.addAll(content);

        long dataProcessingEndTime = System.currentTimeMillis();
        log.info("----------- renewalFindPageCache Data processing time: {}ms", dataProcessingEndTime - redisLockEndTime);

        try {
            String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
            log.info("----------- renewalFindPageCache stakeReward find page redis write pvoStr: {}", pvoStr);
            log.info("----------- renewalFindPageCache stakeReward find page redis write CountKey: {}", page.getTotalElements());
            redisService.set(stakeRewardPageKey, pvoStr, 15, TimeUnit.MINUTES);
            redisService.set(stakeRewardPageCountKey, String.valueOf(page.getTotalElements()), 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("----------- renewalFindPageCache StakeReward find page redis write error: {}", e.getMessage());
        }
        long redisWriteEndTime = System.currentTimeMillis();
        log.info("----------- renewalFindPageCache Redis write time: {}ms", redisWriteEndTime - dataProcessingEndTime);
        redisService.del(stakeRewardQueryKey);
        long endTime = System.currentTimeMillis();
        log.info("----------- renewalFindPageCache Total execution time: {}ms", endTime - startTime);
        return new PageImpl<>(stakeRewards, pageable, page.getTotalElements());
    }

    public Page<GridStakeReward> findPage(String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        long startTime = System.currentTimeMillis();
        log.info("----------- findPage startTime:{}", startTime);
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (epoch.equals(currentEpoch) && ("stakingAmount".equalsIgnoreCase(orderBy) || "stakingReward".equalsIgnoreCase(orderBy) || "validStakingAmount".equalsIgnoreCase(orderBy) || "validStakingQuota".equalsIgnoreCase(orderBy))){
            return findCurrentEpochPageOrderHelper(pageSize, pageNum, orderBy, sorted);
        }
        // 构造Key的逻辑封装
        String stakeRewardPageKey = buildKey("stakeRewardPage", epoch, pageSize, pageNum, orderBy, sorted);
        String stakeRewardPageCountKey = buildKey("stakeRewardPageCount", epoch, pageSize, pageNum, orderBy, sorted);

        List<GridStakeReward> stakeRewards = new ArrayList<>();
        try {
            Object listValue = redisService.get(stakeRewardPageKey);
            log.info("----------- stakeReward find page redis read listValue: {}", listValue);
            Object countValue = redisService.get(stakeRewardPageCountKey);
            log.info("----------- stakeReward find page redis read countValue: {}", countValue);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                stakeRewards = JSONObject.parseArray(v, GridStakeReward.class);
                if (!stakeRewards.isEmpty()){
                    long endTime = System.currentTimeMillis();
                    log.info("----------- findPage Redis read time: " + (endTime - startTime) + "ms");
                    return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("----------- StakeReward find page redis read error: {}", e.getMessage());
        }

        long redisReadEndTime = System.currentTimeMillis();
        log.info("----------- Redis read time: {}ms", redisReadEndTime - startTime);

        String stakeRewardQueryKey = buildKey("stakeRewardQueryKey", epoch, pageSize, pageNum, orderBy, sorted);
        if (!redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 30, TimeUnit.SECONDS )) {
            throw new RuntimeException("The system is busy, please try again later");
        }

        long redisLockEndTime = System.currentTimeMillis();
        log.info("----------- Redis lock time: {}ms", redisLockEndTime - redisReadEndTime);

        Sort sort = resolveSort(orderBy, sorted);
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);

        Specification<GridStakeReward> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(epoch)) {
                return criteriaBuilder.equal(root.get("epoch"), epoch);
            }
            return null;
        };

        Page<GridStakeReward> page = stakeRewardRepository.findAll(specification, pageable);
        List<GridStakeReward> content = page.getContent();
        if (epoch.equalsIgnoreCase(currentEpoch)){
            StakeRewardOverview overview = stakingRewardOverviewService.findByEpoch(epoch);
            if (null != overview){
                String validStakingAmountTotal = overview.getValidStakingAmount();
                String currentEpochReward = overview.getCurrentEpochReward();
                if (!"0".equals(validStakingAmountTotal)){
                    for (GridStakeReward stakeReward : content) {
                        String validStakingAmount = new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString();
                        stakeReward.setValidStakingAmount(validStakingAmount);
                        String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(validStakingAmountTotal),6, RoundingMode.HALF_UP).toString();
                        stakeReward.setValidStakingQuota(validStakingQuota);
                        stakeReward.setStakingReward(new BigDecimal(validStakingQuota).multiply(new BigDecimal(currentEpochReward)).setScale(0, RoundingMode.HALF_UP).toString());
                    }
                }
            }
        }
        stakeRewards.addAll(content);

        long dataProcessingEndTime = System.currentTimeMillis();
        log.info("----------- Data processing time: {}ms", dataProcessingEndTime - redisLockEndTime);

        try {
            String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
            log.info("----------- stakeReward find page redis write pvoStr: {}", pvoStr);
            log.info("----------- stakeReward find page redis write CountKey: {}", page.getTotalElements());
            if (epoch.equalsIgnoreCase(currentEpoch)){
                redisService.set(stakeRewardPageKey, pvoStr, 15, TimeUnit.MINUTES);
                redisService.set(stakeRewardPageCountKey, String.valueOf(page.getTotalElements()), 15, TimeUnit.MINUTES);
            } else {
                redisService.set(stakeRewardPageKey, pvoStr, 24, TimeUnit.HOURS);
                redisService.set(stakeRewardPageCountKey, String.valueOf(page.getTotalElements()), 24, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("----------- StakeReward find page redis write error: {}", e.getMessage());
        }
        long redisWriteEndTime = System.currentTimeMillis();
        log.info("----------- Redis write time: {}ms", redisWriteEndTime - dataProcessingEndTime);
        redisService.del(stakeRewardQueryKey);
        long endTime = System.currentTimeMillis();
        log.info("----------- Total execution time: {}ms", endTime - startTime);
        return new PageImpl<>(stakeRewards, pageable, page.getTotalElements());
    }

    private String buildKey(String prefix, String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        keyBuilder.append(":epoch:").append(epoch);
        appendIfNotEmpty(keyBuilder, ":pageSize:", pageSize);
        appendIfNotEmpty(keyBuilder, ":pageNum:", pageNum);
        appendIfNotEmpty(keyBuilder, ":orderBy:", orderBy);
        appendIfNotEmpty(keyBuilder, ":sorted:", sorted);
        return keyBuilder.toString();
    }

    private Sort resolveSort(String orderBy, String sorted) {
        Sort sort;
        if ("livingRatio".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("livingRatio");
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingReward");
        } else if ("validStakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingAmount");
        } else if ("validStakingQuota".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingQuota");
        } else {
            sort = Sort.by("createTime");
        }
        return "DESC".equalsIgnoreCase(sorted) ? sort.descending() : sort.ascending();
    }

    private void appendIfNotEmpty(StringBuilder builder, String suffix, Object value) {
        if (ObjectUtils.isNotEmpty(value)) {
            builder.append(suffix).append(value);
        }
    }


    public Page<GridStakeReward> findCurrentEpochPageOrderHelper(int pageSize, int pageNum, String orderBy, String sortDirection) {
        String epoch = web3jUtils.getCurrentEpoch();
        String cacheKey = buildCacheKey("currentEpochStakeReward", epoch, pageSize, pageNum, orderBy, sortDirection);
        String countCacheKey = buildCacheKey("currentEpochStakeRewardCount", epoch, pageSize, pageNum, orderBy, sortDirection);

        Map<String, String> result = loadFromCacheOrDatabase(cacheKey, countCacheKey, epoch, pageSize, pageNum, orderBy, sortDirection);
        String rewards = result.get("stakeRewards");
        JSONArray jsonArray = JSONArray.parseArray(rewards.toString());
        List<GridStakeReward> stakeRewards = JSONArray.parseArray(jsonArray.toJSONString(), GridStakeReward.class);
        String size = result.getOrDefault("size", "0");
        return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Integer.parseInt(size.toString()));
    }

    private String buildCacheKey(String prefix, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection) {
        StringBuilder sb = new StringBuilder(prefix).append(":epoch:").append(epoch);
        if (pageSize > 0) sb.append(":pageSize:").append(pageSize);
        if (pageNum > 0) sb.append(":pageNum:").append(pageNum);
        if (orderBy != null && !orderBy.isEmpty()) sb.append(":orderBy:").append(orderBy);
        if (sortDirection != null && !sortDirection.isEmpty()) sb.append(":sortDirection:").append(sortDirection);
        return sb.toString();
    }

    private Map<String, String> loadFromCacheOrDatabase(String cacheKey, String countCacheKey, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection) {
        Map<String, String> result = new HashMap<>();
        List<GridStakeReward> stakeRewards = new ArrayList<>();
        try {

            Object listValue = redisService.get(cacheKey);
            Object countValue = redisService.get(countCacheKey);

            if (listValue != null && countValue != null) {
                JSONArray jsonArray = JSONArray.parseArray(listValue.toString());
                long size = Long.parseLong(countValue.toString());
                result.put("size", String.valueOf(size));
                stakeRewards = JSONArray.parseArray(jsonArray.toJSONString(), GridStakeReward.class);
                if (!stakeRewards.isEmpty()) {
                    String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    result.put("stakeRewards", pvoStr);
                    return result;
                }
            }
        } catch (NumberFormatException e) {
            log.error("Error parsing cache count value", e);
        } catch (Exception e) {
            log.error("Error reading from cache", e);
        }

        return loadFromDatabaseAndCacheResults(cacheKey, countCacheKey, epoch, pageSize, pageNum, orderBy, sortDirection, stakeRewards);
    }

    private Map<String,String> loadFromDatabaseAndCacheResults(String cacheKey, String countCacheKey, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection, List<GridStakeReward> stakeRewards) {
        String stakeRewardQueryKey = buildCacheKey("stakeRewardCurrentEpochQuery", epoch, pageSize, pageNum, orderBy, sortDirection);
        Boolean b = redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 30, TimeUnit.SECONDS);
        if (!b){
            throw new RuntimeException("The system is busy, please try again later");
        }
        Map<String, String> result = new HashMap<>();
        if (stakeRewards.isEmpty()) {
            try {
                if (stakeRewards.isEmpty()) { // Double-checked locking
                    List<GridStakeReward> all = findAllByEpoch(epoch);
                    result.put("size", String.valueOf(all.size()));
                    countStakeReward(all, epoch);
                    stakeRewards = pageHelper(pageSize, pageNum, orderBy, sortDirection, all);
                    String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    result.put("stakeRewards", pvoStr);
                    cacheResults(cacheKey, countCacheKey, stakeRewards, all.size());
                }
            }catch (Exception e){
                log.error("Error reading from database", e);
            }
        }
        redisService.del(stakeRewardQueryKey);
        return result;
    }

    private void cacheResults(String cacheKey, String countCacheKey, List<GridStakeReward> stakeRewards, long count) {
        try {
            String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(cacheKey, pvoStr, 15, TimeUnit.MINUTES);
            redisService.set(countCacheKey, String.valueOf(count), 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error writing to cache", e);
        }
    }

    @NotNull
    private List<GridStakeReward> pageHelper(int pageSize, int pageNum, String orderBy, String sorted, List<GridStakeReward> stakeRewards) {
        Comparator<GridStakeReward> comparator = null;
        if ("livingRatio".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getLivingRatio()));
        } else if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingAmount()));
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingReward()));
        } else if ("validStakingAmount".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getValidStakingAmount()));
        } else if ("validStakingQuota".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getValidStakingQuota()));
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

    public List<GridStakeReward> list(String epoch){
        return stakeRewardRepository.findAllByEpoch(epoch);
    }

    public String userTotalStakingReward(String address){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByStakingProviderAndEpochNot(address, currentEpoch);
        BigDecimal total = BigDecimal.ZERO;
        for (GridStakeReward stakeReward : stakeRewards) {
            String stakingReward = stakeReward.getStakingReward();
            if (StringUtils.isNotEmpty(stakingReward)){
                total = total.add(new BigDecimal(stakingReward));
            }
        }
        List<GridStakeReward> currentEpochStakeRewards = stakeRewardRepository.findAllByEpoch(currentEpoch);
        countStakeReward(currentEpochStakeRewards, currentEpoch);
        for (GridStakeReward currentEpochStakeReward : currentEpochStakeRewards) {
            if (address.equalsIgnoreCase(currentEpochStakeReward.getTokenId())){
                total = total.add(new BigDecimal(currentEpochStakeReward.getStakingReward()));
            }
        }
        return total.toString();
    }

    public boolean checkAllOnlineWithinOneEpoch(String stakingProvider){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        int i = stakeRewardRepository.countStakingProviderAllOnlineEpoch(stakingProvider, currentEpoch);
        return i > 0;
    }

    public List<GridStakeReward> findAllByEpoch(String epoch){
        List<GridStakeReward> stakeRewards;
        String stakeRewardEpochKey = "stakeRewardEpoch:" + epoch;
        try {
            Object listValue = redisService.get(stakeRewardEpochKey);
            if (null != listValue) {
                String v = listValue.toString();
                return JSONObject.parseArray(v, GridStakeReward.class);
            }
        }catch (Exception e){
            log.error("stakeReward findAllByEpoch redis read error：{}", e.getMessage());
        }
        stakeRewards = findAllByEpoch(epoch, 10000);
        if (!stakeRewards.isEmpty()){
            try {
                String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(stakeRewardEpochKey, pvoStr, 5, TimeUnit.MINUTES);
            }catch (Exception e){
                log.error("stakeReward findAllByEpoch redis write error：{}", e.getMessage());
            }
        }
        return stakeRewards;
    }

    public List<GridStakeReward> findAllByEpoch(String epoch, int batchSize) {
        try {
            List<GridStakeReward> stakeRewards = new ArrayList<>();
            Pageable pageable = PageRequest.of(0, batchSize);
            Page<GridStakeReward> currentPage;

            do {
                currentPage = stakeRewardRepository.findAllByEpoch(epoch, pageable);
                stakeRewards.addAll(currentPage.getContent());
                pageable = pageable.next();
            } while (currentPage.hasNext());

            return stakeRewards;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public int getTotalGridAmount(){
        return stakeRewardRepository.countTotalNode();
    }
}