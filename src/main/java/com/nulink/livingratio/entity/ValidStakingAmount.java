package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "valid_staking_amount")
public class ValidStakingAmount extends BaseEntity{

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "staking_amount")
    private String stakingAmount;

}
