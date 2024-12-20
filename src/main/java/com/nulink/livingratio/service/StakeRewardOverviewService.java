package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.repository.CreateNodePoolEventRepository;
import com.nulink.livingratio.repository.StakeRewardOverviewRepository;
import com.nulink.livingratio.repository.GridStakeRewardRepository;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class StakeRewardOverviewService {

    private final StakeRewardOverviewRepository stakeRewardOverviewRepository;
    private final GridStakeRewardRepository gridStakeRewardRepository;
    private final Web3jUtils web3jUtils;
    private final CreateNodePoolEventRepository createNodePoolEventRepository;

    private final RedisService redisService;

    public StakeRewardOverviewService(StakeRewardOverviewRepository stakeRewardOverviewRepository,
                                      GridStakeRewardRepository stakeRewardRepository,
                                      Web3jUtils web3jUtils,
                                      CreateNodePoolEventRepository createNodePoolEventRepository,
                                      RedisService redisService) {
        this.stakeRewardOverviewRepository = stakeRewardOverviewRepository;
        this.gridStakeRewardRepository = stakeRewardRepository;
        this.web3jUtils = web3jUtils;
        this.createNodePoolEventRepository = createNodePoolEventRepository;
        this.redisService = redisService;
    }

    @Async
//    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void generateStakeRewardOverview(){
        String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
        if (null == stakeRewardOverviewRepository.findByEpoch(previousEpoch)){
            List<GridStakeReward> previousEpochStakeReward = gridStakeRewardRepository.findAllByEpoch(previousEpoch);
            if (!previousEpochStakeReward.isEmpty()){
                stakeRewardOverviewRepository.save(getStakeRewardOverview(previousEpochStakeReward, previousEpoch));
            }else {
                StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
                List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(previousEpoch));
                List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
                String sum = sum(reward);
                sum = new BigDecimal(sum).add(new BigDecimal(web3jUtils.getEpochReward(previousEpoch))).toString();
                stakeRewardOverview.setEpoch(previousEpoch);
                stakeRewardOverview.setAccumulatedReward(sum);
                stakeRewardOverview.setCurrentEpochReward(web3jUtils.getEpochReward(previousEpoch));
                stakeRewardOverviewRepository.save(stakeRewardOverview);
            }
        }
    }

    public StakeRewardOverview getStakeRewardOverview(List<GridStakeReward> stakeRewards, String epoch){
        Set<String> tokenIds = stakeRewards.stream().map(GridStakeReward::getTokenId).collect(Collectors.toSet());
        List<CreateNodePoolEvent> events = createNodePoolEventRepository.findAll();
        // add all node pool
        events.forEach(event -> {
            tokenIds.add(event.getTokenId());
        });
        StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
        if (!stakeRewards.isEmpty()){
            for (GridStakeReward stakeReward : stakeRewards) {
                if (StringUtils.isNotEmpty(stakeReward.getLivingRatio())){
                    stakeReward.setValidStakingAmount(new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
            stakeRewardOverview.setValidStakingAmount(sum(stakeRewards.stream().map(GridStakeReward::getValidStakingAmount).filter(StringUtils::isNotEmpty).collect(Collectors.toList())));
            stakeRewardOverview.setTotalStakingAmount(sum(stakeRewards.stream().map(GridStakeReward::getStakingAmount).filter(StringUtils::isNotEmpty).collect(Collectors.toList())));
            stakeRewardOverview.setTotalStakingNodes(String.valueOf(tokenIds.size()));
        }
        List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(epoch));
        List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        String sum = sum(reward);
        String epochReward = web3jUtils.getEpochReward(epoch);
        sum = new BigDecimal(sum).add(new BigDecimal(epochReward)).toString();
        stakeRewardOverview.setAccumulatedReward(sum);
        stakeRewardOverview.setCurrentEpochReward(epochReward);
        stakeRewardOverview.setEpoch(epoch);
        return stakeRewardOverview;
    }

    public StakeRewardOverview findLastEpoch(String epoch){
        String stakeRewardOverviewFindEpoch = "StakeRewardOverview:lastEpoch:" + epoch;
        try {
            Object redisValue = redisService.get(stakeRewardOverviewFindEpoch);
            if (null != redisValue) {
                String v = redisValue.toString();
                return JSONObject.parseObject(v, StakeRewardOverview.class);
            }
        }catch (Exception e){
            log.error("StakeRewardOverview findLastEpoch redis read error：{}", e.getMessage());
        }
        StakeRewardOverview rewardOverview = stakeRewardOverviewRepository.findByEpoch(epoch);
        if (null != rewardOverview){
            try {
                String pvoStr = JSON.toJSONString(rewardOverview, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(stakeRewardOverviewFindEpoch, pvoStr, 15, TimeUnit.MINUTES);
            }catch (Exception e){
                log.error("StakeRewardOverview findLastEpoch redis write error：{}", e.getMessage());
            }
        }
        return rewardOverview;
    }

    public StakeRewardOverview findCurrentEpoch(){
        String epoch = web3jUtils.getCurrentEpoch();
        String stakeRewardOverviewCurrentEpoch = "StakeRewardOverview:currentEpoch:" + epoch;
        StakeRewardOverview stakeRewardOverview;
        try {
            Object redisValue = redisService.get(stakeRewardOverviewCurrentEpoch);
            if (ObjectUtils.isNotEmpty(redisValue) && null != redisValue && !"null".equals(redisValue.toString())) {
                log.info("redisValue:{}", redisValue.toString());
                String v = redisValue.toString();
                return JSONObject.parseObject(v, StakeRewardOverview.class);
            }
        }catch (Exception e){
            log.error("StakeRewardOverview findCurrentEpoch redis read error", e.fillInStackTrace());
        }
        stakeRewardOverview = stakeRewardOverviewRepository.findByEpoch(epoch);
        log.info("load overview from db:{}", stakeRewardOverview);
        if (null == stakeRewardOverview){
            List<GridStakeReward> stakeRewards = gridStakeRewardRepository.findAllByEpoch(epoch);
            if (!stakeRewards.isEmpty()){
                log.info("stakeRewards list size", stakeRewards.size());
                stakeRewardOverview = getStakeRewardOverview(stakeRewards, epoch);
            } else {
                stakeRewardOverview = new StakeRewardOverview();
                List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(epoch));
                List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
                String sum = sum(reward);
                String epochReward = web3jUtils.getEpochReward(epoch);
                sum = new BigDecimal(sum).add(new BigDecimal(epochReward)).toString();
                stakeRewardOverview.setAccumulatedReward(sum);
                stakeRewardOverview.setCurrentEpochReward(epochReward);
                log.info("accumulatedReward:{}", sum);
            }
        }
        try {
            String pvoStr = JSON.toJSONString(stakeRewardOverview, SerializerFeature.WriteNullStringAsEmpty);
            log.info("redis write:{}", pvoStr);
            redisService.set(stakeRewardOverviewCurrentEpoch, pvoStr, 20, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("StakeRewardOverview findCurrentEpoch redis write error：{}", e.getMessage());
        }
        return stakeRewardOverview;
    }

    public StakeRewardOverview findByEpoch(String epoch){
        String stakeRewardOverviewCurrentEpoch = "StakeRewardOverview:currentEpoch:" + epoch;
        StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
        try {
            Object redisValue = redisService.get(stakeRewardOverviewCurrentEpoch);
            if (null != redisValue) {
                String v = redisValue.toString();
                return JSONObject.parseObject(v, StakeRewardOverview.class);
            }
        }catch (Exception e){
            log.error("StakeRewardOverview findCurrentEpoch redis read error：{}", e.getMessage());
        }
        stakeRewardOverview = stakeRewardOverviewRepository.findByEpoch(epoch);
        try {
            String pvoStr = JSON.toJSONString(stakeRewardOverview, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(stakeRewardOverviewCurrentEpoch, pvoStr, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("StakeRewardOverview findCurrentEpoch redis write error：{}", e.getMessage());
        }
        return stakeRewardOverview;
    }

    private String sum(List<String> amount){
        BigDecimal sum = BigDecimal.ZERO;
        for (String s : amount) {
            sum = sum.add(new BigDecimal(s));
        }
        return sum.toString();
    }

    public Map<String, String> findTotal(){
        List<StakeRewardOverview> stakeRewardOverviewList = stakeRewardOverviewRepository.findAll();
        StakeRewardOverview currentEpoch = findCurrentEpoch();
        if (currentEpoch != null) {
            stakeRewardOverviewList.add(currentEpoch);
        }
        Map<String, String> result = new HashMap<>();
        if (!stakeRewardOverviewList.isEmpty()){
            List<String> validStakingAmounts = stakeRewardOverviewList.stream().filter(stakeRewardOverview -> StringUtils.isNotEmpty(stakeRewardOverview.getValidStakingAmount())).map(StakeRewardOverview::getValidStakingAmount).collect(Collectors.toList());
            List<String> totalStakingAmounts = stakeRewardOverviewList.stream().filter(stakeRewardOverview -> StringUtils.isNotEmpty(stakeRewardOverview.getTotalStakingAmount())).map(StakeRewardOverview::getTotalStakingAmount).collect(Collectors.toList());
            result.put("validStakingAmount", sum(validStakingAmounts));
            result.put("totalStakingAmount", sum(totalStakingAmounts));
        }
        return result;
    }

    public void saveByEpoch(StakeRewardOverview stakeRewardOverview){
        StakeRewardOverview overview = stakeRewardOverviewRepository.findByEpoch(stakeRewardOverview.getEpoch());
        if (null != overview){
            overview.setAccumulatedReward(stakeRewardOverview.getAccumulatedReward());
            overview.setCurrentEpochReward(stakeRewardOverview.getCurrentEpochReward());
            overview.setTotalStakingAmount(stakeRewardOverview.getTotalStakingAmount());
            overview.setValidStakingAmount(stakeRewardOverview.getValidStakingAmount());
            overview.setTotalStakingNodes(stakeRewardOverview.getTotalStakingNodes());
            stakeRewardOverviewRepository.save(overview);
        } else {
            stakeRewardOverviewRepository.save(stakeRewardOverview);
        }
    }
}
