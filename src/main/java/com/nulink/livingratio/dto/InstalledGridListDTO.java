package com.nulink.livingratio.dto;

import lombok.Data;

@Data
public class InstalledGridListDTO {

    private String epoch;

    private String tokenId;

    private String userAddress;

    private String stakingAmount;

    private String stakingReward;

    private String workAddress;

    private String ipAddress;

    private boolean isOnline;

    private String currentFeeRatio;

    private String nextFeeRatio;

    private String feeIncome;

    private String gridAddress;

}
