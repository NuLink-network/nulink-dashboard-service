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
import com.nulink.livingratio.repository.NodePoolEventsRepository;
import com.nulink.livingratio.repository.PersonalStakingOverviewRepository;
import com.nulink.livingratio.repository.ValidPersonalStakingAmountRepository;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigInteger;
import java.sql.Timestamp;
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

    private final NodePoolEventsRepository nodePoolEventsRepository;

    private final GridStakingDetailRepository gridStakingDetailRepository;
    private final Web3jUtils web3jUtils;

    public PersonalStakingOverviewService(ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository,
                                          RedisService redisService,
                                          PersonalStakingOverviewRepository personalStakingOverviewRepository,
                                          NodePoolEventsRepository nodePoolEventsRepository,
                                          GridStakingDetailRepository gridStakingDetailRepository, Web3jUtils web3jUtils) {
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
        this.redisService = redisService;
        this.personalStakingOverviewRepository = personalStakingOverviewRepository;
        this.nodePoolEventsRepository = nodePoolEventsRepository;
        this.gridStakingDetailRepository = gridStakingDetailRepository;
        this.web3jUtils = web3jUtils;
    }

    public PersonalStakingOverviewRecord findByTokenIdAndUserAddress(String tokenId, String userAddress) {
        return personalStakingOverviewRepository.findByTokenIdAndUserAddress(tokenId, userAddress);
    }

    @Transactional
    public void handleStakingEvent(NodePoolEvents nodePoolEvents) {
        String user = nodePoolEvents.getUser();
        String tokenId = nodePoolEvents.getTokenId();
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(user, nodePoolEvents.getEpoch(),
                        tokenId,
                        nodePoolEvents.getTxHash(),
                        NodePoolEventEnum.STAKING.getName());

        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(user, nodePoolEvents.getCreateTime());
        if (stakingOverview == null){
            // first staking
            newPersonalStakingOverviewRecord.setTotalStakingAmount(nodePoolEvents.getAmount());
            newPersonalStakingOverviewRecord.setPendingStakingAmount(nodePoolEvents.getAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(1);
            newPersonalStakingOverviewRecord.setClaimablePrincipleAmount("0");
            newPersonalStakingOverviewRecord.setPendingPrincipleAmount("0");
            newPersonalStakingOverviewRecord.setReceivedRewardAmount("0");
            newPersonalStakingOverviewRecord.setCreateTime(nodePoolEvents.getCreateTime());
            newPersonalStakingOverviewRecord.setLastUpdateTime(nodePoolEvents.getCreateTime());
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        } else {
            log.info("+++++++++++++++++++++++++++++nodePoolEvents staking amount:{}", nodePoolEvents.getAmount());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(new BigInteger(stakingOverview.getTotalStakingAmount())
                    .add(new BigInteger(nodePoolEvents.getAmount())).toString());

            newPersonalStakingOverviewRecord.setTotalStakingGrid(calculateStakingGrid(user, nodePoolEvents.getTxHash(), nodePoolEvents.getCreateTime(), nodePoolEvents.getTokenId()));
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());
                newPersonalStakingOverviewRecord.setPendingStakingAmount(new BigInteger(stakingOverview.getPendingStakingAmount())
                        .add(new BigInteger(nodePoolEvents.getAmount())).toString());
            } else {
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount("0");
                newPersonalStakingOverviewRecord.setPendingStakingAmount(new BigInteger(nodePoolEvents.getAmount()).toString());
                // claimable principle amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(StringUtils.isBlank(stakingOverview.getClaimablePrincipleAmount())? "0" : stakingOverview.getClaimablePrincipleAmount())
                                .add(new BigInteger(StringUtils.isBlank(stakingOverview.getPendingPrincipleAmount())? "0" : stakingOverview.getPendingPrincipleAmount())).toString());
            }
            newPersonalStakingOverviewRecord.setCreateTime(nodePoolEvents.getCreateTime());
            newPersonalStakingOverviewRecord.setLastUpdateTime(nodePoolEvents.getCreateTime());
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    private Integer calculateStakingGrid(String user, String currentTxHash, Timestamp createTime, String eventTokenId){
        List<NodePoolEvents> nodePoolEvents = nodePoolEventsRepository.findAllByUserAndTxHashNotAndCreateTimeBeforeOrderByCreateTime(user, currentTxHash, createTime);
        Set<String> tokenIds = new java.util.HashSet<>();
        for (NodePoolEvents nodePoolEvent : nodePoolEvents) {
            String tokenId = nodePoolEvent.getTokenId();
            if (nodePoolEvent.getEvent().equals("STAKING")){
                tokenIds.add(tokenId);
            } else if (nodePoolEvent.getEvent().equals("UN_STAKING")){
                tokenIds.remove(tokenId);
            }
        }
        tokenIds.add(eventTokenId);
        return tokenIds.size();
    }

    @Transactional
    public void handleUnStakingEvent(NodePoolEvents nodePoolEvents) {
        String user = nodePoolEvents.getUser();
        String tokenId = nodePoolEvents.getTokenId();
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(user,
                        nodePoolEvents.getEpoch(),
                        tokenId,
                        nodePoolEvents.getTxHash(),
                        NodePoolEventEnum.UN_STAKING.getName());
        PersonalStakingOverviewRecord stakingOverview = personalStakingOverviewRepository.findFirstByUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(nodePoolEvents.getUser(), nodePoolEvents.getCreateTime());
        if (stakingOverview != null){
            String lockAmount = nodePoolEvents.getLockAmount();
            String unlockAmount = nodePoolEvents.getUnlockAmount();
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid() - 1);
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(new BigInteger(stakingOverview.getTotalStakingAmount())
                    .subtract(new BigInteger(nodePoolEvents.getAmount())).toString());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){

                newPersonalStakingOverviewRecord.setPendingStakingAmount(new BigInteger(stakingOverview.getPendingStakingAmount())
                        .subtract(new BigInteger(unlockAmount)).toString());

                newPersonalStakingOverviewRecord.setPendingPrincipleAmount(new BigInteger(stakingOverview.getPendingPrincipleAmount())
                        .add(new BigInteger(lockAmount)).toString());

                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());

            } else {

                newPersonalStakingOverviewRecord.setPendingStakingAmount("0");

                newPersonalStakingOverviewRecord.setPendingPrincipleAmount(lockAmount);

                // epoch change, claimable principle amount = claimable principle amount + pending principle amount + unlock amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).add(new BigInteger(stakingOverview.getPendingPrincipleAmount())).toString());
            }
            newPersonalStakingOverviewRecord.setCreateTime(nodePoolEvents.getCreateTime());
            newPersonalStakingOverviewRecord.setLastUpdateTime(nodePoolEvents.getCreateTime());
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    @Transactional
    public void handleClaimEvent(NodePoolEvents nodePoolEvents) {
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(nodePoolEvents.getUser(),
                        nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTokenId(),
                        nodePoolEvents.getTxHash(),
                        NodePoolEventEnum.CLAIM.getName());
        PersonalStakingOverviewRecord stakingOverview =
                personalStakingOverviewRepository.findFirstByUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(nodePoolEvents.getUser(), nodePoolEvents.getCreateTime());
        if (stakingOverview != null){
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(stakingOverview.getReceivedRewardAmount());
            newPersonalStakingOverviewRecord.setTotalStakingAmount(stakingOverview.getTotalStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid());

            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){

                newPersonalStakingOverviewRecord.setPendingStakingAmount(stakingOverview.getPendingStakingAmount());
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());

                // epoch not changed, claimable principle amount = claimable principle amount - claim event amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).subtract(new BigInteger(nodePoolEvents.getAmount())).toString());
            } else {

                newPersonalStakingOverviewRecord.setPendingStakingAmount("0");
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount("0");

                // epoch changed, claimable principle amount = pending principle amount + claimable principle amount - claim event amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount())
                                .add(new BigInteger(stakingOverview.getPendingPrincipleAmount()))
                                .subtract(new BigInteger(nodePoolEvents.getAmount())).toString());
            }
            newPersonalStakingOverviewRecord.setCreateTime(nodePoolEvents.getCreateTime());
            newPersonalStakingOverviewRecord.setLastUpdateTime(nodePoolEvents.getCreateTime());
            personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
        }
    }

    @Transactional
    public void handleClaimRewardEvent(NodePoolEvents nodePoolEvents) {
        PersonalStakingOverviewRecord newPersonalStakingOverviewRecord =
                new PersonalStakingOverviewRecord(nodePoolEvents.getUser(),
                        nodePoolEvents.getEpoch(),
                        nodePoolEvents.getTokenId(),
                        nodePoolEvents.getTxHash(),
                        NodePoolEventEnum.CLAIM_REWARD.getName());
        PersonalStakingOverviewRecord stakingOverview =
                personalStakingOverviewRepository.findFirstByUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(nodePoolEvents.getUser(), nodePoolEvents.getCreateTime());
        if (stakingOverview != null){
            newPersonalStakingOverviewRecord.setTotalStakingAmount(stakingOverview.getTotalStakingAmount());
            newPersonalStakingOverviewRecord.setTotalStakingGrid(stakingOverview.getTotalStakingGrid());
            newPersonalStakingOverviewRecord.setReceivedRewardAmount(new BigInteger(stakingOverview.getReceivedRewardAmount()).add(new BigInteger(nodePoolEvents.getAmount())).toString());
            if (nodePoolEvents.getEpoch().equals(stakingOverview.getEpoch())){

                newPersonalStakingOverviewRecord.setPendingStakingAmount(stakingOverview.getPendingStakingAmount());
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount(stakingOverview.getPendingPrincipleAmount());

                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(stakingOverview.getClaimablePrincipleAmount());
            } else {

                newPersonalStakingOverviewRecord.setPendingStakingAmount("0");
                newPersonalStakingOverviewRecord.setPendingPrincipleAmount("0");

                // epoch change, claimable principle amount = claimable principle amount + pending principle amount
                newPersonalStakingOverviewRecord.setClaimablePrincipleAmount(
                        new BigInteger(stakingOverview.getClaimablePrincipleAmount()).add(new BigInteger(stakingOverview.getPendingPrincipleAmount())).toString());
            }
        }
        newPersonalStakingOverviewRecord.setCreateTime(nodePoolEvents.getCreateTime());
        newPersonalStakingOverviewRecord.setLastUpdateTime(nodePoolEvents.getCreateTime());
        personalStakingOverviewRepository.save(newPersonalStakingOverviewRecord);
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
            if (new BigInteger(epoch).compareTo(new BigInteger(overviewRecord.getEpoch())) == 0){
                userStakingOverviewDTO.setPendingStakingAmount(overviewRecord.getPendingStakingAmount());
                userStakingOverviewDTO.setClaimablePrinciple(overviewRecord.getClaimablePrincipleAmount());
            } else if (new BigInteger(epoch).compareTo(new BigInteger(overviewRecord.getEpoch())) > 0){
                userStakingOverviewDTO.setPendingStakingAmount("0");
                userStakingOverviewDTO.setClaimablePrinciple(new BigInteger(overviewRecord.getClaimablePrincipleAmount()).add(new BigInteger(overviewRecord.getPendingPrincipleAmount())).toString());
            }

            List<GridStakingDetail> stakingDetails = gridStakingDetailRepository.findByUserAddress(userAddress);
            BigInteger accumulatedReward = BigInteger.ZERO;
            for (GridStakingDetail gridStakingDetail : stakingDetails){
                accumulatedReward = accumulatedReward.add(new BigInteger(gridStakingDetail.getStakingReward()));
            }
            String receivedRewardAmount = overviewRecord.getReceivedRewardAmount();
            if (StringUtils.isBlank(receivedRewardAmount)){
                receivedRewardAmount = "0";
            }
            BigInteger calaimableReward = accumulatedReward.subtract(new BigInteger(receivedRewardAmount));
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

    public List<PersonalStakingOverviewRecord> findAllByUserAddressAndCreateTimeAfter(String userAddress, Timestamp createTime){
        return personalStakingOverviewRepository.findAllByUserAddressAndCreateTimeAfter(userAddress, createTime);
    }

    public void deleteAll(List<PersonalStakingOverviewRecord> personalStakingOverviewRecords){
        personalStakingOverviewRepository.deleteAll(personalStakingOverviewRecords);
    }
}

