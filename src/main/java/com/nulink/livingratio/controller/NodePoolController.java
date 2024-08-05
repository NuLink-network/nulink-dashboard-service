package com.nulink.livingratio.controller;

import com.nulink.livingratio.service.CreateNodePoolEventService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "Stake Grid")
@RestController
@RequestMapping("stakeGrid")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class NodePoolController {

    private final CreateNodePoolEventService createNodePoolEventService;

    public NodePoolController(CreateNodePoolEventService createNodePoolEventService) {
        this.createNodePoolEventService = createNodePoolEventService;
    }

    @GetMapping("countStakeGrids")
    public BaseResponse<Map<String, Integer>> countStakeGrids() {
        int availableGrids = createNodePoolEventService.findAll().size();
        Integer auctionGrids = createNodePoolEventService.stakeGridsForAuction();
        Map<String, Integer> size = Map.of("availableGrids", availableGrids, "auctionGrids", auctionGrids);
        return BaseResponse.success(size);
    }

}
