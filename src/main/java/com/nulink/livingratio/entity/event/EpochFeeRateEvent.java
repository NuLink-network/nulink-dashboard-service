package com.nulink.livingratio.entity.event;

import com.nulink.livingratio.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "epoch_fee_rate_event", indexes = {
        @Index(name = "token_id_index", columnList = "token_id"),
        @Index(name = "tx_hash_index", columnList = "tx_hash")
})
public class EpochFeeRateEvent extends BaseEntity {

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "epoch")
    private String epoch;

    @Column(name = "fee_rate")
    private String feeRate;

    @Column(name = "token_id")
    private String tokenId;

}
