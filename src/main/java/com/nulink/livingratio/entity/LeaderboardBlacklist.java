package com.nulink.livingratio.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "leaderboard_blacklist")
public class LeaderboardBlacklist extends BaseEntity {

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "is_deleted", columnDefinition = "bit(1) DEFAULT b'0'")
    private boolean deleted = false;

}
