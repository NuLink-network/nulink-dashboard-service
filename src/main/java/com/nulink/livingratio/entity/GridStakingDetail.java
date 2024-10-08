package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "grid_staking_detail", indexes = {
        @javax.persistence.Index(name = "epoch_index", columnList = "epoch"),
        @javax.persistence.Index(name = "tokenId_index", columnList = "token_id"),
        @Index(name = "user_address_index", columnList = "user_address")
})
public class GridStakingDetail extends BaseEntity{

    @Column(name = "user_address")
    private String userAddress;

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

    @Column(name = "is_valid", columnDefinition = "boolean default false")
    private boolean isValid;

    @Transient
    private String fee;

    @Transient
    private Integer index;

}
