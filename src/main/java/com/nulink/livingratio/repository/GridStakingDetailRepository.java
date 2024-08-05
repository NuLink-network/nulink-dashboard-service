package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.entity.GridStakingDetail;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GridStakingDetailRepository extends PagingAndSortingRepository<GridStakingDetail, Long>, JpaSpecificationExecutor {

    List<GridStakingDetail> findByEpochAndTokenId(String epoch, String gridId);

    List<GridStakingDetail> findByUserAddress(String userAddress);

    @Query(value = "select * from grid_staking_detail g where g.user_address = :userAddress and g.epoch + 0 < :epoch and g.token_id = :gridId order by g.create_time desc", nativeQuery = true)
    List<GridStakingDetail> findAllByUserAddressAndEpochLessThanAndTokenIdOrderByCreateTimeDesc(String userAddress, String epoch, String gridId);

}
