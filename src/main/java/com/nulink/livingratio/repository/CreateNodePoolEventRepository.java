package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreateNodePoolEventRepository extends PagingAndSortingRepository<CreateNodePoolEvent, Long>, JpaSpecificationExecutor {

    CreateNodePoolEvent findByTxHash(String txHash);

    List<CreateNodePoolEvent> findAll();

    CreateNodePoolEvent findByTokenId(String tokenId);

}
