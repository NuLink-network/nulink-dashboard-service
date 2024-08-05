package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidPersonalStakingAmountRepository extends JpaRepository<ValidPersonalStakingAmount, Long>, JpaSpecificationExecutor {

    List<ValidPersonalStakingAmount> findAllByTokenIdAndEpochLessThanEqual(String tokenId, Integer epoch);

    List<ValidPersonalStakingAmount> findAllByTokenId(String tokenId);

    ValidPersonalStakingAmount findFirstByTokenIdAndUserAddressOrderByCreateTimeDesc(String tokenId, String userAddress);

    List<ValidPersonalStakingAmount> findAllByUserAddress(String userAddress);

}
