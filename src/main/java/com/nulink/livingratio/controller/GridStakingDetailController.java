package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.GridStakingDetail;
import com.nulink.livingratio.service.GridStakingDetailService;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "Grid Staking Detail")
@RestController
@RequestMapping("gridStakingDetail")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class GridStakingDetailController {

    private final GridStakingDetailService gridStakingDetailService;

    private final RedisService redisService;

    public GridStakingDetailController(GridStakingDetailService gridStakingDetailService, RedisService redisService) {
        this.gridStakingDetailService = gridStakingDetailService;
        this.redisService = redisService;
    }

    @ApiOperation(value = "Staking Reward detail by tokenId")
    @GetMapping("page")
    public BaseResponse<Page<GridStakingDetail>> findStakeRewardPage(@RequestParam(value = "epoch") String epoch,
                                                                     @RequestParam(value = "tokenId") String tokenId,
                                                                     @RequestParam(value = "orderBy", required = false) String orderBy,
                                                                     @RequestParam(value = "sorted", required = false) String sorted,
                                                                     @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                     @RequestParam(value = "pageNum", defaultValue = "1") int pageNum){
        return BaseResponse.success(gridStakingDetailService.findPage(epoch, tokenId, pageSize, pageNum, orderBy, sorted));
    }

    /*@ApiOperation(value = "User Staking Overview")
    @GetMapping("userStakingOverview")
    public BaseResponse<UserStakingOverviewDTO> userStakingOverview(@RequestParam(value = "address") String address) {
        return BaseResponse.success(gridStakingDetailService.userStakingOverview(address));
    }*/
}
