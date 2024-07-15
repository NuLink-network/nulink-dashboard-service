package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.EpochFeeRateEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EpochFeeRateEventRepository extends PagingAndSortingRepository<EpochFeeRateEvent, Long>, JpaSpecificationExecutor {

    EpochFeeRateEvent findByTxHash(String txHash);

    EpochFeeRateEvent findByEpochAndTokenId(String epoch, String tokenId);

}
