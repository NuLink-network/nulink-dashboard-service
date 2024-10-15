package com.nulink.livingratio;

import com.nulink.livingratio.dto.UserStakingOverviewDTO;
import com.nulink.livingratio.service.ContractOffsetService;
import com.nulink.livingratio.service.CreateNodePoolEventService;
import com.nulink.livingratio.service.PersonalStakingOverviewService;
import com.nulink.livingratio.service.SetLivingRatioService;
import com.nulink.livingratio.utils.Web3jUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@SpringBootTest
class ApplicationTests {

	@Autowired
	PersonalStakingOverviewService personalStakingOverviewService;
	@Autowired
	ContractOffsetService contractOffsetService;
	@Autowired
	SetLivingRatioService setLivingRatioService;
	@Autowired
	Web3jUtils web3jUtils;

	@Test
	void contextLoads() throws IOException, ExecutionException, InterruptedException {
		String s = web3jUtils.setLiveRatio("28", "13", List.of("19131300000000000000"), List.of("370000000000000000000"));
		System.out.println(s);
	}

}
