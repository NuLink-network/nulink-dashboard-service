package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.repository.CreateNodePoolEventRepository;
import com.nulink.livingratio.utils.NodePoolMapSingleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Slf4j
@Service
public class CreateNodePoolEventService {

    private final CreateNodePoolEventRepository createNodePoolEventRepository;

    public CreateNodePoolEventService(CreateNodePoolEventRepository createNodePoolEventRepository) {
        this.createNodePoolEventRepository = createNodePoolEventRepository;
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

}
