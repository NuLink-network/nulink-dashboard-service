package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreateNodePoolEventRepository extends PagingAndSortingRepository<CreateNodePoolEvent, Long>, JpaSpecificationExecutor {

    CreateNodePoolEvent findByTxHash(String txHash);

    List<CreateNodePoolEvent> findAll();

    CreateNodePoolEvent findByTokenId(String tokenId);

    @Query(value = "select count(1) from card_slot cs where start_timestamp <= :currentTime and end_timestamp > :currentTime", nativeQuery = true)
    Integer stakeGridsForAuction(long currentTime);

    List<CreateNodePoolEvent> findAllByOwnerAddress(String ownerAddress);

}
