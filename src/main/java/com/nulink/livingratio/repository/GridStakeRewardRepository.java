package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.GridStakeReward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GridStakeRewardRepository extends PagingAndSortingRepository<GridStakeReward, Long>, JpaSpecificationExecutor {

    GridStakeReward findByEpochAndStakingProvider(String epoch, String stakingProvider);

    List<GridStakeReward> findAllByEpoch(String epoch);

    Page<GridStakeReward> findAllByEpoch(String epoch, Pageable pageable);

    List<GridStakeReward> findAllByEpochAndLivingRatioNot(String epoch, String stakingReward);

    List<GridStakeReward> findAllByStakingProvider(String StakingProvider);

    List<GridStakeReward> findAllByStakingProviderAndEpochNot(String stakingProvider, String epoch);

    @Query(value = "SELECT count(1) FROM grid_stake_reward sr where token_id = :tokenId and SUBSTR(sr.living_ratio, 1, 1) = '1' and epoch != :currentEpoch", nativeQuery = true)
    int countStakingProviderAllOnlineEpoch(@Param("tokenId") String tokenId, @Param("currentEpoch") String currentEpoch);

    GridStakeReward findByEpochAndTokenId(String epoch, String tokenId);

    @Query(value = "select count(distinct grid_stake_reward.token_id) from grid_stake_reward where staking_reward != '0'", nativeQuery = true)
    int countTotalNode();

}
