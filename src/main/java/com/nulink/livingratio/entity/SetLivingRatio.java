package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "set_living_ratio", indexes = {
        @javax.persistence.Index(name = "epoch_index", columnList = "epoch"),
        @javax.persistence.Index(name = "token_id_index", columnList = "token_id")
})
public class SetLivingRatio extends BaseEntity{

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "set_living_ratio")
    private boolean setLivingRatio;

    @Column(name = "transaction_fail", columnDefinition = "bit(1) DEFAULT b'0'")
    private boolean transactionFail;

    @Column(name = "reason",  columnDefinition = " text")
    private String reason;

}
