package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.event.Bond;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BondRepository extends PagingAndSortingRepository<Bond, Long>, JpaSpecificationExecutor {

    Bond findByTxHash(String txHash);

    @Query(value = "select * from bond where staking_provider = :stakingProvider and operator != '0x0000000000000000000000000000000000000000' order by create_time desc limit 0,1", nativeQuery = true)
    Bond findLastOneBondByStakingProvider(@Param("stakingProvider") String stakingProvider);

    Bond findFirstByStakingProviderOrderByCreateTimeDesc(String stakingProvider);

}
