package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.Bond;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BondRepository extends PagingAndSortingRepository<Bond, Long>, JpaSpecificationExecutor {

    Bond findByTxHash(String txHash);

    Bond findFirstByStakingProviderOrderByCreateTimeDesc(String stakingProvider);

}
