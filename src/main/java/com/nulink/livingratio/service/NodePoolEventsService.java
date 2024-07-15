package com.nulink.livingratio.service;

import com.nulink.livingratio.constant.NodePoolEventEnum;
import com.nulink.livingratio.entity.NodePoolEvents;
import com.nulink.livingratio.repository.NodePoolEventsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Slf4j
@Service
public class NodePoolEventsService {

    private final NodePoolEventsRepository nodePoolEventsRepository;

    private final ValidPersonalStakingAmountService validPersonalStakingAmountService;

    public NodePoolEventsService(NodePoolEventsRepository nodePoolEventsRepository, ValidPersonalStakingAmountService validPersonalStakingAmountService) {
        this.nodePoolEventsRepository = nodePoolEventsRepository;
        this.validPersonalStakingAmountService = validPersonalStakingAmountService;
    }

    @Transactional
    public void save(NodePoolEvents nodePoolEvents) {
        NodePoolEvents event = nodePoolEventsRepository.findByTxHash(nodePoolEvents.getTxHash());
        if (event != null) {
            return;
        }
        if (nodePoolEvents.getEvent().equals(NodePoolEventEnum.STAKING.getName()) || nodePoolEvents.getEvent().equals(NodePoolEventEnum.UN_STAKING.getName())){
            validPersonalStakingAmountService.updateValidPersonalStakingAmount(nodePoolEvents);
        }
        nodePoolEventsRepository.save(nodePoolEvents);
    }
}
