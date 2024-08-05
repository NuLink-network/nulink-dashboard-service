package com.nulink.livingratio;

import com.nulink.livingratio.dto.UserStakingOverviewDTO;
import com.nulink.livingratio.service.PersonalStakingOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {

	@Autowired
	PersonalStakingOverviewService personalStakingOverviewService;

	@Test
	void contextLoads() {
		UserStakingOverviewDTO userStakingOverview = personalStakingOverviewService.findUserStakingOverview("0x0", "1");
		System.out.println(1);
	}

}
