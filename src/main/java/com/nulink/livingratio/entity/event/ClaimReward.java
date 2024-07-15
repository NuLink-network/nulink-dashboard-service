package com.nulink.livingratio.entity.event;

import com.nulink.livingratio.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "claim_reward", indexes = {
        @javax.persistence.Index(name = "tx_hash_index", columnList = "tx_hash", unique = true),
        @javax.persistence.Index(name = "user_index", columnList = "user")
})
public class ClaimReward extends BaseEntity {

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "user")
    private String user;

    @Column(name = "reward_amount")
    private String rewardAmount;

    @Column(name = "time")
    private String time;

    @Column(name = "epoch")
    private String epoch;

}
