package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "grid")
public class Grid extends BaseEntity{

    private String tokenId;

    private String userAddress;

    private boolean installed;

}
