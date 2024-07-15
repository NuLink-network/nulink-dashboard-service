package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "stake_reward_overview")
public class StakeRewardOverview extends BaseEntity{

    @Column(name = "valid_staking_amount")
    private String validStakingAmount;

    @Column(name = "total_staking_amount")
    private String totalStakingAmount;

    @Column(name = "current_epoch_reward")
    private String currentEpochReward;

    @Column(name = "accumulated_reward")
    private String accumulatedReward;

    @Column(name = "total_staking_nodes")
    private String totalStakingNodes;

    @Column(name = "epoch")
    private String epoch;

}
