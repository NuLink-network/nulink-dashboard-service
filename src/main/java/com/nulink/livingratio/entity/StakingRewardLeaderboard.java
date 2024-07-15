package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "stake_reward_leaderboard")
public class StakingRewardLeaderboard extends BaseEntity{

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "accumulated_staking_reward")
    private String accumulatedStakingReward;

    @Column(name = "ranking")
    private int ranking;

    @Column(name = "epoch")
    private String epoch;

}
