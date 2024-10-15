package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.entity.event.SendFeeEvent;
import com.nulink.livingratio.repository.SendFeeEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import javax.transaction.Transactional;

@Service
public class SendFeeEventService {

    private final SendFeeEventRepository sendFeeEventRepository;

    public SendFeeEventService(SendFeeEventRepository sendFeeEventRepository) {
        this.sendFeeEventRepository = sendFeeEventRepository;
    }

    public SendFeeEvent findByTxHash(String txHash) {
        return sendFeeEventRepository.findByTxHash(txHash);
    }

    @Transactional
    public void save(SendFeeEvent sendFeeEvent) {
        if (null == (sendFeeEventRepository.findByTxHash(sendFeeEvent.getTxHash()))){
            sendFeeEventRepository.save(sendFeeEvent);
        }
    }

    public Page<SendFeeEvent> findPage(String user, String epoch, String tokenId, int pageSize, int pageNum) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC,"createTime"));
        Specification<NodePoolEvents> specification = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (StringUtils.hasLength(epoch)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("epoch"), epoch));
            }
            if (StringUtils.hasLength(tokenId)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("tokenId"), tokenId));
            }
            if (StringUtils.hasLength(user)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("user"), user));
            }
            return predicate;
        };
        return sendFeeEventRepository.findAll(specification, pageable);
    }
}
