package com.nulink.livingratio.entity.event;

import com.nulink.livingratio.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "staking_event", indexes = {
        @Index(name = "tx_hash_index", columnList = "tx_hash", unique = true),
        @Index(name = "user_index", columnList = "user"),
        @Index(name = "epoch_index", columnList = "epoch"),
        @Index(name = "token_id_index", columnList = "token_id")
})
public class StakingEvent extends BaseEntity {

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "user")
    private String user;

    @Column(name = "amount")
    private String amount = "0";

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "event")
    private String event;

    @Column(name = "token_id")
    private String tokenId;

}
