package com.nulink.livingratio.dto;

import lombok.Data;

@Data
public class GridStakingDetailDTO extends BaseDto{

    private String userAddress;

    private String stakingAmount;

    private String stakingQuota;

    private String epoch;

    private String feeRatio;

    private String stakingReward;

    private String gridId;

}
