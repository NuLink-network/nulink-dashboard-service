package com.nulink.livingratio.controller;

import com.nulink.livingratio.dto.InstalledGridListDTO;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.service.CreateNodePoolEventService;
import com.nulink.livingratio.service.EpochFeeRateEventService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Api(tags = "Stake Grid")
@RestController
@RequestMapping("stakeGrid")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class NodePoolController {

    private final Web3jUtils web3jUtils;

    private final CreateNodePoolEventService createNodePoolEventService;

    private final EpochFeeRateEventService epochFeeRateEventService;

    public NodePoolController(Web3jUtils web3jUtils, CreateNodePoolEventService createNodePoolEventService,
                              EpochFeeRateEventService epochFeeRateEventService) {
        this.web3jUtils = web3jUtils;
        this.createNodePoolEventService = createNodePoolEventService;
        this.epochFeeRateEventService = epochFeeRateEventService;
    }

    @GetMapping("countStakeGrids")
    public BaseResponse<Map<String, Integer>> countStakeGrids() {
        int availableGrids = createNodePoolEventService.findAll().size();
        Integer auctionGrids = createNodePoolEventService.stakeGridsForAuction();
        Map<String, Integer> size = Map.of("availableGrids", availableGrids, "auctionGrids", auctionGrids);
        return BaseResponse.success(size);
    }

    @GetMapping("installedList")
    public BaseResponse<Page<InstalledGridListDTO>> installedList(@RequestParam(value = "userAddress") String userAddress,
                                                                  @RequestParam(value = "epoch") String epoch,
                                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                  @RequestParam(value = "pageNum", defaultValue = "1") int pageNum) {
        return BaseResponse.success(createNodePoolEventService.findInstalledGridListDTO(userAddress, epoch, pageSize, pageNum));
    }

    @GetMapping("getFeeRateFollowingNextEpoch")
    public BaseResponse<String> getFeeRateFollowingNextEpoch(@RequestParam(value = "tokenId") String tokenId) {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String followingNextEpoch = String.valueOf(Integer.parseInt(currentEpoch) + 2);
        return BaseResponse.success(epochFeeRateEventService.getFeeRate(tokenId, followingNextEpoch));
    }

}
