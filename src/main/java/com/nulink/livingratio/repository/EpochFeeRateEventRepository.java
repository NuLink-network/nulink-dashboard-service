package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.EpochFeeRateEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpochFeeRateEventRepository extends PagingAndSortingRepository<EpochFeeRateEvent, Long>, JpaSpecificationExecutor {

    EpochFeeRateEvent findByTxHash(String txHash);

    EpochFeeRateEvent findByEpochAndTokenId(String epoch, String tokenId);

    List<EpochFeeRateEvent> findAllByEpoch(String epoch);

    @Query(value = "select * from epoch_fee_rate_event e where (e.epoch + 0) <= :epoch and e.token_id = :tokenId order by e.create_time desc limit 1 ",
            nativeQuery = true)
    EpochFeeRateEvent findFirstByEpochAndTokenIdOrderByCreateTimeDesc(@Param("epoch") String epoch, @Param("tokenId") String tokenId);

}
