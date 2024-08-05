package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "valid_personal_staking_amount", indexes = {
        @javax.persistence.Index(name = "user_address_index", columnList = "user_address"),
        @javax.persistence.Index(name = "epoch_index", columnList = "epoch"),
        @javax.persistence.Index(name = "token_id_index", columnList = "token_id")
})
public class ValidPersonalStakingAmount extends BaseEntity{

    @Column(name = "user_address")
    private String userAddress;

    @Column(name = "epoch")
    private Integer epoch;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "staking_amount")
    private String stakingAmount;

    @Column(name = "tx_hash")
    private String txHash;

}
