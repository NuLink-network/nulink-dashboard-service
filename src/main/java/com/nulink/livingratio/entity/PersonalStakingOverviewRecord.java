package com.nulink.livingratio.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "personal_staking_overview_record", indexes = {
        @Index(name = "epoch_index", columnList = "epoch"),
        @Index(name = "token_id_index", columnList = "token_id"),
        @Index(name = "user_address_index", columnList = "user_address"),
        @Index(name = "tx_hash_index", columnList = "tx_hash", unique = true)
})
public class PersonalStakingOverviewRecord extends BaseEntity{

    @Column(name = "user_address", columnDefinition = "varchar(42)")
    private String userAddress;

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "event")
    private String event;

    @Column(name = "total_staking_amount", columnDefinition = "varchar(255) DEFAULT '0'")
    private String totalStakingAmount;

    @Column(name = "total_staking_grid", columnDefinition = "integer DEFAULT 0")
    private Integer totalStakingGrid;

    @Column(name = "claimable_principle_amount", columnDefinition = "varchar(255) DEFAULT '0'")
    private String claimablePrincipleAmount;

    @Column(name = "pending_staking_amount", columnDefinition = "varchar(255) DEFAULT '0'")
    private String pendingStakingAmount;

    @Column(name = "pending_principle_amount", columnDefinition = "varchar(255) DEFAULT '0'")
    private String pendingPrincipleAmount;

    // received reward amount
    @Column(name = "received_reward_amount", columnDefinition = "varchar(255) DEFAULT '0'")
    private String receivedRewardAmount;

    public PersonalStakingOverviewRecord() {
    }

    public PersonalStakingOverviewRecord(String userAddress, String epoch, String tokenId, String txHash, String event) {
        this.userAddress = userAddress;
        this.epoch = epoch;
        this.tokenId = tokenId;
        this.txHash = txHash;
        this.event = event;
    }
}
