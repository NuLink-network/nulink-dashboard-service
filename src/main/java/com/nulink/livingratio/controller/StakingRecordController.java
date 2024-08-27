package com.nulink.livingratio.controller;

import com.nulink.livingratio.dto.UserStakingOverviewDTO;
import com.nulink.livingratio.entity.event.NodePoolEvents;
import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import com.nulink.livingratio.service.GridStakingDetailService;
import com.nulink.livingratio.service.NodePoolEventsService;
import com.nulink.livingratio.service.PersonalStakingOverviewService;
import com.nulink.livingratio.service.ValidPersonalStakingAmountService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "Staking Record")
@RestController
@RequestMapping("stakingRecord")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class StakingRecordController {

    private final ValidPersonalStakingAmountService validPersonalStakingAmountService;

    private final NodePoolEventsService nodePoolEventsService;

    private final PersonalStakingOverviewService personalStakingOverviewService;
    private final GridStakingDetailService gridStakingDetailService;

    public StakingRecordController(ValidPersonalStakingAmountService validPersonalStakingAmountService,
                                   NodePoolEventsService nodePoolEventsService, PersonalStakingOverviewService personalStakingOverviewService, GridStakingDetailService gridStakingDetailService) {
        this.validPersonalStakingAmountService = validPersonalStakingAmountService;
        this.nodePoolEventsService = nodePoolEventsService;
        this.personalStakingOverviewService = personalStakingOverviewService;
        this.gridStakingDetailService = gridStakingDetailService;
    }

    @ApiOperation(value = "Staking amount by stake grids")
    @GetMapping("findValidStaking")
    public BaseResponse<Page<ValidPersonalStakingAmount>> findValidStakingAmountByUser(@RequestParam(name = "userAddress") String userAddress,
                                                                               @RequestParam(name = "epoch") String epoch,
                                                                               @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                                               @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum){
        Page<ValidPersonalStakingAmount> allByUserAddress = validPersonalStakingAmountService.findAllByUserAddress(userAddress, epoch, pageSize, pageNum);
        return BaseResponse.success(allByUserAddress);
    }

    @ApiOperation(value = "operation records")
    @GetMapping("operationRecords")
    public BaseResponse<Page<NodePoolEvents>> findAllByTokenId(@RequestParam(name = "userAddress") String userAddress,
                                                               @RequestParam(name = "tokenId") String tokenId,
                                                               @RequestParam(name = "epoch") String epoch,
                                                               @ApiParam(name = "event", value = "STAKING,UN_STAKE,CLAIM,CLAIM_REWARD") @RequestParam(name = "event", required = false) String event,
                                                               @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                               @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum){
        return BaseResponse.success(nodePoolEventsService.findByUserAddress(userAddress, tokenId, event, epoch, pageSize, pageNum));
    }

    @ApiOperation(value = "Staking overview")
    @GetMapping("overView")
    public BaseResponse<UserStakingOverviewDTO> findByUserAddressStakingOverview(@RequestParam(name = "userAddress") String userAddress,
                                                                                 @RequestParam(name = "epoch") String epoch){
        return BaseResponse.success(personalStakingOverviewService.findUserStakingOverview(userAddress, epoch));
    }

    @ApiOperation(value = "Reward Data")
    @GetMapping("rewardData")
    public BaseResponse<Map<String, String>> rewardData(@RequestParam(name = "userAddress") String userAddress,
                                                        @RequestParam(name = "epoch") String epoch,
                                                        @RequestParam(name = "tokenId") String tokenId){
        Map<String, String> rewardData = gridStakingDetailService.findRewardData(userAddress, epoch, tokenId);
        return BaseResponse.success(rewardData);
    }
}
