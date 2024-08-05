package com.nulink.livingratio.dto;

import io.swagger.annotations.ApiModel;
import lombok.Data;

@Data
@ApiModel
public class UserStakingOverviewDTO {

    private String tokenId;

    private String userAddress;

    private String epoch;

    private Integer stakeGrids;

    private String stakingAmountInPool;

    private String pendingStakingAmount;

    private String claimablePrinciple;

    private String claimableReward;

    private String accumulatedReward;

}
