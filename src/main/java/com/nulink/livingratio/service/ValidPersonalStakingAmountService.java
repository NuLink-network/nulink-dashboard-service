package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import com.nulink.livingratio.repository.ValidPersonalStakingAmountRepository;
import com.nulink.livingratio.utils.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ValidPersonalStakingAmountService {

    private static String STAKE_EVENT = "STAKING";

    private static String UN_STAKE_EVENT = "UN_STAKING";

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    private final RedisService redisService;

    public ValidPersonalStakingAmountService(ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository,
                                             RedisService redisService) {
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
        this.redisService = redisService;
    }

    @Transactional
    public void updateValidPersonalStakingAmount(NodePoolEvents staking){
        String stakeUser = staking.getUser();
        String epoch =  staking.getEpoch();
        String tokenId = staking.getTokenId();
        String event = staking.getEvent();

        ValidPersonalStakingAmount stakingAmount = validPersonalStakingAmountRepository.findByTxHash(staking.getTxHash());
        if (null != stakingAmount){
            return;
        }

        // Get the last record in tokenId and userAddress
        ValidPersonalStakingAmount personalStakingAmount =
                validPersonalStakingAmountRepository.findFirstByTokenIdAndUserAddressAndCreateTimeBefore(tokenId, stakeUser, staking.getCreateTime());

        ValidPersonalStakingAmount newStakingAmount = new ValidPersonalStakingAmount();
        newStakingAmount.setUserAddress(stakeUser);
        newStakingAmount.setTokenId(tokenId);
        newStakingAmount.setEpoch(Integer.parseInt(epoch));
        newStakingAmount.setTxHash(staking.getTxHash());
        newStakingAmount.setCreateTime(staking.getCreateTime());
        newStakingAmount.setLastUpdateTime(staking.getLastUpdateTime());
        if (null != personalStakingAmount){
            if (STAKE_EVENT.equalsIgnoreCase(event)){
                newStakingAmount.setStakingAmount(new BigDecimal(personalStakingAmount.getStakingAmount()).add(new BigDecimal(staking.getAmount())).toString());
            }
        } else {
            if (STAKE_EVENT.equalsIgnoreCase(event)){
                newStakingAmount.setStakingAmount(staking.getAmount());
            }
        }
        if (UN_STAKE_EVENT.equalsIgnoreCase(event)){
            newStakingAmount.setStakingAmount("0");
        }
        validPersonalStakingAmountRepository.save(newStakingAmount);
    }

    public Page<ValidPersonalStakingAmount> findAllByUserAddress(String userAddress, String epoch, Integer pageSize, Integer pageNum){

        String cacheKey = "validPersonalStakingAmount:" + userAddress + ":" + pageNum + ":" + pageSize;
        String countCacheKey = "validPersonalStakingAmount_count:" + userAddress + ":" + pageNum + ":" + pageSize;

        List<ValidPersonalStakingAmount> validPersonalStakingAmounts = null;

        try {
            Object listValue = redisService.get(cacheKey);
            Object countValue = redisService.get(countCacheKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                validPersonalStakingAmounts = JSONObject.parseArray(v, ValidPersonalStakingAmount.class);
                if (!validPersonalStakingAmounts.isEmpty()){
                    return new PageImpl<>(validPersonalStakingAmounts, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("----------- ValidPersonalStakingAmount findAllByUserAddress redis read error: {}", e.getMessage());
        }

        List<ValidPersonalStakingAmount> stakingAmounts =
                validPersonalStakingAmountRepository.findAllByUserAddressAndEpochLessThanEqual(userAddress, Integer.parseInt(epoch));

        stakingAmounts = stakingAmounts.stream().filter(stakingAmount -> StringUtils.isNotBlank(stakingAmount.getStakingAmount()))
                .filter(stakingAmount -> !"0".equalsIgnoreCase(stakingAmount.getStakingAmount())).collect(Collectors.toList());

        int endIndex = pageNum * pageSize;
        int size = stakingAmounts.size();
        endIndex = Math.min(endIndex, size);
        validPersonalStakingAmounts = stakingAmounts.subList((pageNum - 1) * pageSize, endIndex);

        if (ObjectUtils.isNotEmpty(validPersonalStakingAmounts)){
            try {
                String pvoStr = JSON.toJSONString(validPersonalStakingAmounts, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(cacheKey, pvoStr, 20, TimeUnit.SECONDS);
                redisService.set(countCacheKey, stakingAmounts.size());
            }catch (Exception e){
                log.error("ValidPersonalStakingAmount findAllByUserAddress  redis write error：{}", e.getMessage());
            }
        }
        return new PageImpl<>(validPersonalStakingAmounts, PageRequest.of(pageNum - 1, pageSize), stakingAmounts.size());
    }

    public List<ValidPersonalStakingAmount> findAllByUserAddressAndCreateTimeAfter(String user, Timestamp createTime){
        return validPersonalStakingAmountRepository.findAllByUserAddressAndCreateTimeAfter(user, createTime);
    }

    public void deleteAll(List<ValidPersonalStakingAmount> validPersonalStakingAmounts){
        validPersonalStakingAmountRepository.deleteAll(validPersonalStakingAmounts);
    }
}
