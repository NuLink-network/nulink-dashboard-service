package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import com.nulink.livingratio.repository.ValidPersonalStakingAmountRepository;
import com.nulink.livingratio.utils.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ValidPersonalStakingAmountService {

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

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

        // Get the last record in tokenId and userAddress
        ValidPersonalStakingAmount personalStakingAmount =
                validPersonalStakingAmountRepository.findFirstByTokenIdAndUserAddressOrderByCreateTimeDesc(tokenId, stakeUser);
        if (null != personalStakingAmount){
            if (epoch.equalsIgnoreCase(String.valueOf(personalStakingAmount.getEpoch()))){
                if (STAKE_EVENT.equalsIgnoreCase(event)){
                    personalStakingAmount.setStakingAmount(new BigDecimal(personalStakingAmount.getStakingAmount()).add(new BigDecimal(staking.getAmount())).toString());
                }
            } else {
                personalStakingAmount = new ValidPersonalStakingAmount();
                personalStakingAmount.setUserAddress(stakeUser);
                personalStakingAmount.setTokenId(tokenId);
                if (STAKE_EVENT.equalsIgnoreCase(event)){
                    personalStakingAmount.setStakingAmount(new BigDecimal(personalStakingAmount.getStakingAmount()).add(new BigDecimal(staking.getAmount())).toString());
                }
            }

        } else {
            personalStakingAmount = new ValidPersonalStakingAmount();
            personalStakingAmount.setUserAddress(stakeUser);
            personalStakingAmount.setTokenId(tokenId);
            if (STAKE_EVENT.equalsIgnoreCase(event)){
                personalStakingAmount.setStakingAmount(staking.getAmount());
            }
        }
        if (UN_STAKE_EVENT.equalsIgnoreCase(event)){
            personalStakingAmount.setStakingAmount("0");
        }
        personalStakingAmount.setEpoch(Integer.parseInt(epoch));
        personalStakingAmount.setTxHash(staking.getTxHash());
        validPersonalStakingAmountRepository.save(personalStakingAmount);
    }

    public ValidPersonalStakingAmount findAllByTokenIdAndUserAddress(String tokenId, String userAddress){
        return validPersonalStakingAmountRepository.findFirstByTokenIdAndUserAddressOrderByCreateTimeDesc(tokenId, userAddress);
    }

    public List<ValidPersonalStakingAmount> findAllByTokenId(String tokenId){
        return validPersonalStakingAmountRepository.findAllByTokenId(tokenId);
    }

    public Page<ValidPersonalStakingAmount> findAllByUserAddress(String userAddress, Integer pageSize, Integer pageNum){

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

        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "tokenId"));

        Specification<ValidPersonalStakingAmount> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(userAddress)) {
                return criteriaBuilder.equal(root.get("userAddress"), userAddress);
            }
            return null;
        };
        Page<ValidPersonalStakingAmount> page = validPersonalStakingAmountRepository.findAll(specification, pageable);
        if (!page.getContent().isEmpty()){
            try {
                String pvoStr = JSON.toJSONString(page.getContent(), SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(cacheKey, pvoStr, 20, TimeUnit.SECONDS);
                redisService.set(countCacheKey, page.getTotalElements());
            }catch (Exception e){
                log.error("ValidPersonalStakingAmount findAllByUserAddress  redis write error：{}", e.getMessage());
            }
        }
        return page;
    }
}
