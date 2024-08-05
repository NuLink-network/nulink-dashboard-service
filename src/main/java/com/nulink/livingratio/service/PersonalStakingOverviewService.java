package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.constant.NodePoolEventEnum;
import com.nulink.livingratio.dto.UserStakingOverviewDTO;
import com.nulink.livingratio.entity.GridStakingDetail;
import com.nulink.livingratio.entity.PersonalStakingOverviewRecord;
import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.repository.GridStakingDetailRepository;
import com.nulink.livingratio.repository.PersonalStakingOverviewRepository;
import com.nulink.livingratio.repository.ValidPersonalStakingAmountRepository;
import com.nulink.livingratio.utils.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PersonalStakingOverviewService {

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    private final RedisService redisService;

    private final PersonalStakingOverviewRepository personalStakingOverviewRepository;

    private final GridStakingDetailRepository gridStakingDetailRepository;

    public PersonalStakingOverviewService(ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository,
                                          RedisService redisService,
                                          PersonalStakingOverviewRepository personalStakingOverviewRepository,
                                          GridStakingDetailRepository gridStakingDetailRepository) {
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
        this.redisService = redisService;
        this.personalStakingOverviewRepository = personalStakingOverviewRepository;
        this.gridStakingDetailRepository = gridStakingDetailRepository;
    }

    public PersonalStakingOverviewRecord findByTokenIdAndUserAddress(String tokenId, String userAddress) {
        return personalStakingOverviewRepository.findByTokenIdAndUserAddress(tokenId, userAddress);
    }

    @Transactional
    public void handleNodePoolEvent(NodePoolEvents nodePoolEvents) {
        switch (nodePoolEvents.getEvent()){
            case "STAKING":
                handleStakingEvent(nodePoolEvents);
                break;
            case "UN_STAKING":
                handleUnStakingEvent(nodePoolEvents);
                break;
            case "CLAIM":
                handleClaimEvent(nodePoolEvents);
                break;
            case "CLAIM_REWARD":
                handleClaimRewardEvent(nodePoolEvents);
                break;
            default:
                log.error("unknown event: {}", nodePoolEvents.getEvent());
        }
    }

    @Transactional
    public void handleStakingEvent(NodePoolEvents nodePoolEvents) {
        String user = nodePoolEvents.getUser();
        String tokenId = nodePoolEvents.getTokenId();
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(user, nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTxHash(),
                        tokenId,
                        NodePoolEventEnum.STAKING.getName());

        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressOrderByCreateTimeDesc(user);
        if (stakingOverview == null){
            // first staking
            newPersonalStakingOverviewRecord.setTotalStakingAmount(nodePoolEvents.getAmount());
            newPersonalStakingOverviewRecord.setPendingStakingAmount(nodePoolEvents.getAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(1);
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        } else {
            List<ValidPersonalStakingAmount> validPersonalStakingAmounts = validPersonalStakingAmountRepository.findAllByUserAddress(user);
            Set<String> tokenIds = validPersonalStakingAmounts.stream().map(ValidPersonalStakingAmount::getTokenId).collect(Collectors.toSet());
            boolean containsTokenId = tokenIds.contains(tokenId);
            newPersonalStakingOverviewRecord.setTotalStakingAmount(new BigInteger(stakingOverview.getTotalStakingAmount())
                    .add(new BigInteger(nodePoolEvents.getAmount())).toString());
            newPersonalStakingOverviewRecord.setPendingStakingAmount(new BigInteger(stakingOverview.getPendingStakingAmount())
                    .add(new BigInteger(nodePoolEvents.getAmount())).toString());
            if (!containsTokenId){
                newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid() + 1);
            }

            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());
            newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());
            } else {
                // claimable principle amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).add(new BigInteger(stakingOverview.getPendingPrincipleAmount())).toString());
            }
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    @Transactional
    public void handleUnStakingEvent(NodePoolEvents nodePoolEvents) {
        String user = nodePoolEvents.getUser();
        String tokenId = nodePoolEvents.getTokenId();
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(user,
                        nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTxHash(),
                        tokenId, NodePoolEventEnum.UN_STAKING.getName());
        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressOrderByCreateTimeDesc(nodePoolEvents.getUser());
        if (stakingOverview != null){
            String lockAmount = nodePoolEvents.getLockAmount();
            String unlockAmount = nodePoolEvents.getUnlockAmount();
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid() - 1);
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());
            newPersonalStakingOverviewRecord.setPendingStakingAmount(new BigInteger(stakingOverview.getPendingStakingAmount())
                    .subtract(new BigInteger(unlockAmount)).toString());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(new BigInteger(stakingOverview.getTotalStakingAmount())
                    .subtract(new BigInteger(unlockAmount)).toString());
            newPersonalStakingOverviewRecord.setPendingPrincipleAmount(new BigInteger(stakingOverview.getPendingPrincipleAmount())
                    .add(new BigInteger(lockAmount)).toString());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());
            } else {
                // epoch change, claimable principle amount = claimable principle amount + pending principle amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).add(new BigInteger(stakingOverview.getPendingPrincipleAmount())).toString());
            }
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    @Transactional
    public void handleClaimEvent(NodePoolEvents nodePoolEvents) {
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(nodePoolEvents.getUser(),
                        nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTxHash(),
                        nodePoolEvents.getTokenId(),
                        NodePoolEventEnum.CLAIM.getName());
        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressOrderByCreateTimeDesc(nodePoolEvents.getUser());
        if (stakingOverview != null){
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());
            newPersonalStakingOverviewRecord.setPendingStakingAmount(stakingOverview.getPendingStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(stakingOverview.getTotalStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid());
            newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){
                // epoch not changed, claimable principle amount = claimable principle amount - claim event amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).subtract(new BigInteger(nodePoolEvents.getAmount())).toString());
            } else {

                // epoch changed, claimable principle amount = pending principle amount + claimable principle amount - claim event amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount())
                                .add(new BigInteger(stakingOverview.getPendingPrincipleAmount()))
                                .subtract(new BigInteger(nodePoolEvents.getAmount())).toString());
            }
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    @Transactional
    public void handleClaimRewardEvent(NodePoolEvents nodePoolEvents) {
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(nodePoolEvents.getUser(),
                        nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTxHash(),
                        nodePoolEvents.getTokenId(),
                        NodePoolEventEnum.CLAIM_REWARD.getName());
        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressOrderByCreateTimeDesc(nodePoolEvents.getUser());
        if (stakingOverview != null){
            newPersonalStakingOverviewRecord.setPendingStakingAmount(stakingOverview.getPendingStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(stakingOverview.getTotalStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid());
            newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(new BigInteger(stakingOverview.getReceivedRewardAmount()).add(new BigInteger(nodePoolEvents.getAmount())).toString());
            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());
            } else {
                // epoch change, claimable principle amount = claimable principle amount + pending principle amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).add(new BigInteger(stakingOverview.getPendingPrincipleAmount())).toString());
            }
        }
    }

    public UserStakingOverviewDTO findUserStakingOverview(String userAddress, String epoch){
        String cacheKey = "UserStakingOverview:" + userAddress + ":" + epoch;
        try {
            Object redisValue = redisService.get(cacheKey);
            if (null != redisValue) {
                String v = redisValue.toString();
                return JSONObject.parseObject(v, UserStakingOverviewDTO.class);
            }
        }catch (Exception e){
            log.error("UserStakingOverview redis read error：{}", e.getMessage());
        }
        UserStakingOverviewDTO userStakingOverviewDTO = new UserStakingOverviewDTO();
        PersonalStakingOverviewRecord overviewRecord = personalStakingOverviewRepository.findFirstByUserAddressAndEpochLessThanEqualOrderByCreateTimeDesc(userAddress, epoch);
        if (overviewRecord != null){
            userStakingOverviewDTO.setUserAddress(userAddress);
            userStakingOverviewDTO.setEpoch(overviewRecord.getEpoch());
            userStakingOverviewDTO.setStakeGrids(overviewRecord.getTotalStakingGrid());
            userStakingOverviewDTO.setStakingAmountInPool(overviewRecord.getTotalStakingAmount());
            userStakingOverviewDTO.setPendingStakingAmount(overviewRecord.getPendingStakingAmount());
            userStakingOverviewDTO.setClaimablePrinciple(overviewRecord.getClaimablePrincipleAmount());
            List<GridStakingDetail> stakingDetails = gridStakingDetailRepository.findByUserAddress(userAddress);
            BigInteger accumulatedReward = BigInteger.ZERO;
            for (GridStakingDetail gridStakingDetail : stakingDetails){
                accumulatedReward = accumulatedReward.add(new BigInteger(gridStakingDetail.getStakingReward()));
            }
            BigInteger calaimableReward = accumulatedReward.subtract(new BigInteger(overviewRecord.getReceivedRewardAmount()));
            userStakingOverviewDTO.setAccumulatedReward(accumulatedReward.toString());
            userStakingOverviewDTO.setClaimableReward(calaimableReward.toString());
        }
        try {
            String pvoStr = JSON.toJSONString(userStakingOverviewDTO, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(cacheKey, pvoStr, 20, TimeUnit.SECONDS);
        }catch (Exception e){
            log.error("StakeRewardOverview findLastEpoch redis write error：{}", e.getMessage());
        }
        return userStakingOverviewDTO;
    }
}

