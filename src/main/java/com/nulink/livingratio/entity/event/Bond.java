package com.nulink.livingratio.entity.event;

import com.nulink.livingratio.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "bond", indexes = {
        @javax.persistence.Index(name = "tx_hash_index", columnList = "tx_hash", unique = true),
        @javax.persistence.Index(name = "staking_provider_index", columnList = "staking_provider"),
        @javax.persistence.Index(name = "operator_index", columnList = "operator")
})
public class Bond extends BaseEntity {

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "operator")
    private String operator;

    @Column(name = "start_timestamp")
    private String startTimestamp;

    @Column(name = "epoch")
    private String epoch;

}
