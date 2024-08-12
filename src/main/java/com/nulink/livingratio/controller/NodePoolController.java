package com.nulink.livingratio.controller;

import com.nulink.livingratio.dto.InstalledGridListDTO;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.service.CreateNodePoolEventService;
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

    @GetMapping("installedList")
    public BaseResponse<Page<InstalledGridListDTO>> installedList(@RequestParam(value = "userAddress") String userAddress,
                                                                  @RequestParam(value = "epoch") String epoch,
                                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                  @RequestParam(value = "pageNum", defaultValue = "1") int pageNum) {
        return BaseResponse.success(createNodePoolEventService.findInstalledGridListDTO(userAddress, epoch, pageSize, pageNum));
    }

}
