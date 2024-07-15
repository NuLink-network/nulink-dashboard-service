package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "node_pool_operation", indexes = {
        @javax.persistence.Index(name = "tx_hash_index", columnList = "tx_hash", unique = true),
        @javax.persistence.Index(name = "user_index", columnList = "user"),
        @Index(name = "epoch_index", columnList = "epoch"),
        @Index(name = "event_index", columnList = "event")
})
public class NodePoolEvents extends BaseEntity{

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "user")
    private String user;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "event")
    private String event;

    @Column(name = "amount")
    private String amount;

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "unlock_amount")
    private String unlockAmount;

    @Column(name = "lock_amount")
    private String lockAmount;

    @Column(name = "last_reward_epoch")
    private String lastRewardEpoch;

}
