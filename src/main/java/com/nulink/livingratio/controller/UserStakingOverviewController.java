package com.nulink.livingratio.controller;

import io.swagger.annotations.Api;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "User Staking Overview")
@RestController
@RequestMapping("userStakingOverview")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class UserStakingOverviewController {


}
