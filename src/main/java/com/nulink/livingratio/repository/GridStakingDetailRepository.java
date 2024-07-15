package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.GridStakingDetail;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GridStakingDetailRepository extends PagingAndSortingRepository<GridStakingDetail, Long>, JpaSpecificationExecutor {

    GridStakingDetail findFirstByEpochAndTokenIdAndUserAddressOrderByCreateTimeDesc(String epoch, String gridId, String userAddress);

    List<GridStakingDetail> findByEpochAndTokenId(String epoch, String gridId);

}
