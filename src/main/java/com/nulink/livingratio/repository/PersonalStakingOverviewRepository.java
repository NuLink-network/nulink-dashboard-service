package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.PersonalStakingOverviewRecord;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface PersonalStakingOverviewRepository extends PagingAndSortingRepository<PersonalStakingOverviewRecord, Long>, JpaSpecificationExecutor {

    PersonalStakingOverviewRecord findByTokenIdAndUserAddress(String tokenId, String userAddress);

    PersonalStakingOverviewRecord findByTokenIdAndUserAddressAndEpoch(String tokenId, String userAddress, String epoch);

    PersonalStakingOverviewRecord findFirstByUserAddressAndCreateTimeBeforeOrderByCreateTimeDesc(String userAddress, Timestamp createTime);

    @Query(value = "select * from personal_staking_overview_record p where p.user_address = :userAddress and p.epoch + 0 <= :epoch order by p.create_time desc limit 1 ", nativeQuery = true)
    PersonalStakingOverviewRecord findFirstByUserAddressAndEpochLessThanEqualOrderByCreateTimeDesc(@Param("userAddress") String userAddress, @Param("epoch") String epoch);

    List<PersonalStakingOverviewRecord> findAllByUserAddressAndCreateTimeAfter(String userAddress, Timestamp createTime);

}
