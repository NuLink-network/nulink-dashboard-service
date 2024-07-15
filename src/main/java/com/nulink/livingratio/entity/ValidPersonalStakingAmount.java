package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "valid_personal_staking_amount")
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
