package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.event.SendFeeEvent;
import com.nulink.livingratio.service.SendFeeEventService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "send fee")
@RestController
@RequestMapping("sendFee")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class SendFeeEventController {

    private final SendFeeEventService sendFeeEventService;

    public SendFeeEventController(SendFeeEventService sendFeeEventService) {
        this.sendFeeEventService = sendFeeEventService;
    }

    @GetMapping("/findPage")
    public BaseResponse<Page<SendFeeEvent>> getSendFeeEvent(@RequestParam(value = "userAddress", required = false) String userAddress,
                                                            @RequestParam(value = "epoch", required = false) String epoch,
                                                            @RequestParam(value = "tokenId", required = false) String tokenId,
                                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum) {
        return BaseResponse.success(sendFeeEventService.findPage(userAddress, epoch, tokenId, pageSize, pageNum));
    }
}
