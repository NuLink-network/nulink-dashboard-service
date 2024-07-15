package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.NodePoolEvents;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface NodePoolEventsRepository extends PagingAndSortingRepository<NodePoolEvents, Long>, JpaSpecificationExecutor {

    NodePoolEvents findByTxHash(String txHash);

}
