package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.NodePoolEvents;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface NodePoolEventsRepository extends PagingAndSortingRepository<NodePoolEvents, Long>, JpaSpecificationExecutor {

    NodePoolEvents findByTxHash(String txHash);

    NodePoolEvents findFirstByUserAndEventOrderByCreateTimeDesc(String user, String event);

    @Query(value = "select * from node_pool_operation n where n.user = :user and n.token_id = :tokenId and n.event = :event and n.epoch + 0 < :epoch ", nativeQuery = true)
    List<NodePoolEvents> findAllByUserAndTokenIdAndEventAndEpochLessThanEqual(String user, String tokenId, String event, String epoch);
}
