package com.nulink.livingratio.entity.event;

import com.nulink.livingratio.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "create_node_pool_event", indexes = {
        @javax.persistence.Index(name = "tx_hash_index", columnList = "tx_hash", unique = true),
        @javax.persistence.Index(name = "node_pool_address_index", columnList = "node_pool_address"),
        @javax.persistence.Index(name = "token_id_index", columnList = "token_id"),
        @javax.persistence.Index(name = "owner_address_index", columnList = "owner_address")
})
public class CreateNodePoolEvent extends BaseEntity {

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "node_pool_address")
    private String nodePoolAddress;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "owner_address")
    private String ownerAddress;

    @Column(name = "fee_ratio")
    private String feeRatio;

}
