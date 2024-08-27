package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.constant.NodePoolEventEnum;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.repository.NodePoolEventsRepository;
import com.nulink.livingratio.utils.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import javax.transaction.Transactional;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NodePoolEventsService {

    private final NodePoolEventsRepository nodePoolEventsRepository;

    private final ValidPersonalStakingAmountService validPersonalStakingAmountService;

    private final PersonalStakingOverviewService personalStakingOverviewService;

    private final RedisService redisService;

    public NodePoolEventsService(NodePoolEventsRepository nodePoolEventsRepository,
                                 ValidPersonalStakingAmountService validPersonalStakingAmountService,
                                 PersonalStakingOverviewService personalStakingOverviewService,
                                 RedisService redisService) {
        this.nodePoolEventsRepository = nodePoolEventsRepository;
        this.validPersonalStakingAmountService = validPersonalStakingAmountService;
        this.personalStakingOverviewService = personalStakingOverviewService;
        this.redisService = redisService;
    }

    @Transactional
    public void save(NodePoolEvents nodePoolEvents) {
        String cacheKey = "NODE_EVENT_HASH_" + nodePoolEvents.getTxHash();

        if (redisService.hasKey(cacheKey)){
            return;
        } else {
            redisService.set(cacheKey, nodePoolEvents.getTxHash(), 60, TimeUnit.SECONDS);
        }
        NodePoolEvents event = nodePoolEventsRepository.findByTxHash(nodePoolEvents.getTxHash());
        if (event != null) {
            return;
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.STAKING.getName()) || nodePoolEvents.getEvent().equals(NodePoolEventEnum.UN_STAKING.getName())){
            validPersonalStakingAmountService.updateValidPersonalStakingAmount(nodePoolEvents);
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.STAKING.getName())){
            personalStakingOverviewService.handleStakingEvent(nodePoolEvents);
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.UN_STAKING.getName())){
            personalStakingOverviewService.handleUnStakingEvent(nodePoolEvents);
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.CLAIM.getName())){
            personalStakingOverviewService.handleClaimEvent(nodePoolEvents);
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.CLAIM_REWARD.getName())){
            personalStakingOverviewService.handleClaimRewardEvent(nodePoolEvents);
        }
        nodePoolEventsRepository.save(nodePoolEvents);
    }

    public Page<NodePoolEvents> findByUserAddress(String userAddress, String tokenId, String event, String epoch, int pageSize, int pageNum) {
        String cacheKey = "nodePoolEvent:" + userAddress;
        String countCacheKey = "nodePoolEventCount:" + userAddress;
        if (StringUtils.hasLength(tokenId)){
            cacheKey = cacheKey + ":" + tokenId;
            countCacheKey = countCacheKey + ":" + tokenId;
        }
        if (StringUtils.hasLength(epoch)){
            cacheKey = cacheKey + ":" + epoch;
            countCacheKey = countCacheKey + ":" + epoch;
        }
        if (StringUtils.hasLength(event)){
            cacheKey = cacheKey + ":" + event;
            countCacheKey = countCacheKey + ":" + event;
        }
        if (!ObjectUtils.isEmpty(pageSize)){
            cacheKey = cacheKey + ":" + pageSize;
            countCacheKey = countCacheKey + ":" + pageSize;
        }
        if (!ObjectUtils.isEmpty(pageNum)){
            cacheKey = cacheKey + ":" + pageNum;
            countCacheKey = countCacheKey + ":" + pageNum;
        }

        List<NodePoolEvents> nodePoolEvents = null;

        try {
            Object listValue = redisService.get(cacheKey);
            Object countValue = redisService.get(countCacheKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                nodePoolEvents = JSONObject.parseArray(v, NodePoolEvents.class);
                if (!nodePoolEvents.isEmpty()){
                    return new PageImpl<>(nodePoolEvents, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("----------- NodePoolEvents findByUserAddress redis read error: {}", e.getMessage());
        }

        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<NodePoolEvents> specification = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasLength(userAddress)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("user"), userAddress));
            }
            if (StringUtils.hasLength(tokenId)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tokenId"), tokenId));
            }
            if (StringUtils.hasLength(epoch)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("epoch"), epoch));
            }
            if (StringUtils.hasLength(event)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("event"), event));
            }
            return predicate;
        };
        Page<NodePoolEvents> page = nodePoolEventsRepository.findAll(specification, pageable);
        if (!page.getContent().isEmpty()){
            try {
                String pvoStr = JSON.toJSONString(page.getContent(), SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(cacheKey, pvoStr, 20, TimeUnit.SECONDS);
                redisService.set(countCacheKey, page.getTotalElements());
            }catch (Exception e){
                log.error("NodePoolEvents findByUserAddress  redis write error：{}", e.getMessage());
            }
        }
        return page;
    }
}
