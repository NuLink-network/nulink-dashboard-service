package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.dto.InstalledGridListDTO;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.entity.GridStakingDetail;
import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.entity.event.Bond;
import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.repository.BondRepository;
import com.nulink.livingratio.repository.CreateNodePoolEventRepository;
import com.nulink.livingratio.utils.NodePoolMapSingleton;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CreateNodePoolEventService {

    private final CreateNodePoolEventRepository createNodePoolEventRepository;

    private final GridStakeRewardService gridStakeRewardService;

    private final EpochFeeRateEventService epochFeeRateEventService;

    private final RedisService redisService;

    private final BondRepository bondRepository;

    private final Web3jUtils web3jUtils;

    public CreateNodePoolEventService(CreateNodePoolEventRepository createNodePoolEventRepository,
                                      GridStakeRewardService gridStakeRewardService,
                                      EpochFeeRateEventService epochFeeRateEventService,
                                      RedisService redisService, BondRepository bondRepository, Web3jUtils web3jUtils) {
        this.createNodePoolEventRepository = createNodePoolEventRepository;
        this.gridStakeRewardService = gridStakeRewardService;
        this.epochFeeRateEventService = epochFeeRateEventService;
        this.redisService = redisService;
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
    }

    @Transactional
    public void save(CreateNodePoolEvent createNodePoolEvent) {
        CreateNodePoolEvent poolEvent = createNodePoolEventRepository.findByTxHash(createNodePoolEvent.getTxHash());
        if (poolEvent != null){
            return;
        }
        NodePoolMapSingleton.put(createNodePoolEvent.getNodePoolAddress(), createNodePoolEvent);
        createNodePoolEventRepository.save(createNodePoolEvent);
    }

    public List<CreateNodePoolEvent> findAll() {
        return createNodePoolEventRepository.findAll();
    }

    public Page findInstalledGridListDTO(String userAddress, String epoch, int pageSize, int pageNum) {
        String cacheKey = "StakeGrid:findInstalledGridListDTO:" + userAddress + ":" + epoch + ":" + pageSize + ":" + pageNum;
        String countCacheKey = "StakeGrid:findInstalledGridListDTO:count:" + userAddress + ":" + epoch+ ":" + pageSize + ":" + pageNum;

        List<InstalledGridListDTO> gridListDTOS = new ArrayList<>();
        try {
            Object listValue = redisService.get(cacheKey);
            Object countValue = redisService.get(countCacheKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                gridListDTOS = JSONObject.parseArray(v, InstalledGridListDTO.class);
                if (!gridListDTOS.isEmpty()){
                    return new PageImpl<>(gridListDTOS, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("----------- StakeGrid findInstalledGridListDTO redis read error: {}", e.getMessage());
        }

        String currentEpoch = web3jUtils.getCurrentEpoch();
        List<GridStakeReward> gridStakeRewards = gridStakeRewardService.findAllByEpochAndStakingProvider(epoch, userAddress);
        if (currentEpoch.equals(epoch)){
            Map<String, GridStakeReward> gridStakeRewardMap = new HashMap<>();
            gridStakeRewards.forEach(gridStakeReward -> {
                gridStakeRewardMap.put(gridStakeReward.getGridAddress(), gridStakeReward);
            });
            List<CreateNodePoolEvent> nodePools = createNodePoolEventRepository.findAllByOwnerAddress(userAddress);
            for (CreateNodePoolEvent nodePool : nodePools) {
                if (!gridStakeRewardMap.containsKey(nodePool.getNodePoolAddress())) {
                    gridStakeRewards.add(new GridStakeReward(nodePool.getNodePoolAddress(), nodePool.getTokenId(), userAddress, epoch));
                }
            }
        }
        gridStakeRewards.sort(Comparator.comparingInt((GridStakeReward o) -> Integer.parseInt(o.getTokenId())).reversed());

        int endIndex = pageNum * pageSize;
        int size = gridStakeRewards.size();
        endIndex = Math.min(endIndex, size);
        List<GridStakeReward> resultList = gridStakeRewards.subList((pageNum - 1) * pageSize, endIndex);
        List<String> gridAddresses = resultList.stream().map(GridStakeReward::getGridAddress).collect(Collectors.toList());
        List<String> nodeAddress;
        try {
            nodeAddress = gridStakeRewardService.findNodeAddress(gridAddresses);
        } catch (Exception e){
            throw new RuntimeException("get ip address error");
        }
        int index = 0;
        for (GridStakeReward gridStakeReward : resultList) {
            InstalledGridListDTO installedGridListDTO = new InstalledGridListDTO();
            if (StringUtils.isEmpty(gridStakeReward.getCurrentFeeRatio())){
                installedGridListDTO.setCurrentFeeRatio(epochFeeRateEventService.getFeeRate(gridStakeReward.getTokenId(), epoch));
            } else {
                installedGridListDTO.setCurrentFeeRatio(gridStakeReward.getCurrentFeeRatio());
            }
            if (StringUtils.isEmpty(gridStakeReward.getNextFeeRatio())){
                installedGridListDTO.setNextFeeRatio(epochFeeRateEventService.getNextFeeRate(gridStakeReward.getTokenId(), epoch));
            } else {
                installedGridListDTO.setNextFeeRatio(gridStakeReward.getNextFeeRatio());
            }
            installedGridListDTO.setUserAddress(gridStakeReward.getStakingProvider());
            installedGridListDTO.setEpoch(epoch);
            Bond bond = bondRepository.findFirstByStakingProviderOrderByCreateTimeDesc(gridStakeReward.getGridAddress());
            if (bond != null && !bond.getOperator().equals("0x0000000000000000000000000000000000000000")) {
                installedGridListDTO.setWorkAddress(bond.getOperator());
            }
            installedGridListDTO.setStakingAmount(gridStakeReward.getStakingAmount());
            installedGridListDTO.setStakingReward(gridStakeReward.getStakingReward());
            installedGridListDTO.setTokenId(gridStakeReward.getTokenId());
            installedGridListDTO.setGridAddress(gridStakeReward.getGridAddress());
            if (!StringUtils.isEmpty(gridStakeReward.getStakingReward()) && !StringUtils.isEmpty(gridStakeReward.getCurrentFeeRatio())){
                installedGridListDTO.setFeeIncome(new BigInteger(gridStakeReward.getStakingReward())
                        .multiply(new BigInteger(gridStakeReward.getCurrentFeeRatio())).divide(new BigInteger("10000")).toString());
            }
            String url = nodeAddress.get(index);
            if (!StringUtils.isEmpty(url)){
                installedGridListDTO.setIpAddress(getIpAddress(url));
                installedGridListDTO.setOnline(gridStakeRewardService.pingNode(url));
            }
            index++;
            gridListDTOS.add(installedGridListDTO);
        }
        try {
            String pvoStr = JSON.toJSONString(gridListDTOS, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(cacheKey, pvoStr, 15, TimeUnit.SECONDS);
            redisService.set(countCacheKey, String.valueOf(gridStakeRewards.size()), 15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("----------- StakeGrid findInstalledGridListDTO redis write error: {}", e.getMessage());
        }
        return new PageImpl<>(gridListDTOS, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(String.valueOf(size)));
    }

    private String getIpAddress(String url){
        if (null == url){
            return null;
        }
        return url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(":"));
    }

    public Integer stakeGridsForAuction() {
        return createNodePoolEventRepository.stakeGridsForAuction();
    }

    public CreateNodePoolEvent findByTokenId(String tokenId) {
        return createNodePoolEventRepository.findByTokenId(tokenId);
    }

}
