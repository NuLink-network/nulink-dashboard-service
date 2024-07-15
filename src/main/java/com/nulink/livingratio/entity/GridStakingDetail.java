package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "grid_staking_detail", indexes = {
        @javax.persistence.Index(name = "epoch_index", columnList = "epoch"),
        @javax.persistence.Index(name = "tokenId_index", columnList = "token_id"),
        @Index(name = "staking_provider_index", columnList = "staking_provider")
})
public class GridStakingDetail extends BaseEntity{

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "staking_amount")
    private String stakingAmount;

    @Column(name = "staking_quota")
    private String stakingQuota;

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "fee_ratio")
    private String feeRatio;

    @Column(name = "staking_reward")
    private String stakingReward;

    @Column(name = "token_id")
    private String tokenId;

}
