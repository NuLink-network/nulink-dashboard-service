package com.nulink.livingratio.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.ColumnDefault;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "grid_stake_reward", indexes = {
        @javax.persistence.Index(name = "token_id_index", columnList = "token_id"),
        @javax.persistence.Index(name = "epoch_index", columnList = "epoch"),
        @javax.persistence.Index(name = "staking_provider_index", columnList = "staking_provider"),
})
public class GridStakeReward extends BaseEntity implements Serializable {

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "grid_address")
    private String gridAddress;

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "operator")
    private String operator;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "staking_amount")
    private String stakingAmount;

    @Column(name = "staking_number")
    private Integer stakingNumber;

    @Column(name = "living_ratio")
    @ColumnDefault("0")
    private String livingRatio;

    @Column(name = "valid_staking_amount")
    @ColumnDefault("0")
    private String validStakingAmount;

    @Column(name = "staking_reward")
    private String stakingReward;

    @Column(name = "valid_staking_quota")
    private String validStakingQuota;

    @Column(name = "connectable")
    @ColumnDefault("0")
    private int connectable = 0;

    @Column(name = "connect_fail")
    @ColumnDefault("0")
    private int connectFail = 0;

    @Column(name = "unstake")
    @ColumnDefault("0")
    private int unStake = 0;

    @Column(name = "ping_count")
    @ColumnDefault("0")
    private int pingCount = 0;

    @Column(name = "epoch")
    private String epoch;

    @Transient
    private boolean online;

    @Column(name = "current_fee_ratio")
    private String currentFeeRatio;

    @Column(name = "next_fee_ratio")
    private String nextFeeRatio;

    public GridStakeReward() {
    }

    public GridStakeReward(String gridAddress, String tokenId, String stakingProvider, String epoch) {
        this.gridAddress = gridAddress;
        this.tokenId = tokenId;
        this.stakingProvider = stakingProvider;
        this.epoch = epoch;
    }
}
