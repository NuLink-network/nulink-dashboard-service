package com.nulink.livingratio;

import com.nulink.livingratio.dto.UserStakingOverviewDTO;
import com.nulink.livingratio.service.ContractOffsetService;
import com.nulink.livingratio.service.PersonalStakingOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigInteger;

@SpringBootTest
class ApplicationTests {

	@Autowired
	PersonalStakingOverviewService personalStakingOverviewService;
	@Autowired
	ContractOffsetService contractOffsetService;

	@Test
	void contextLoads() {
		BigInteger minBlockOffset = contractOffsetService.findMinBlockOffset();
		System.out.println(1);
	}

}
