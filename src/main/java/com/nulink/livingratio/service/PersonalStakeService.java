package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.event.StakingEvent;
import com.nulink.livingratio.repository.PersonalStakeRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonalStakeService {

    private final Web3jUtils web3jUtils;
    private final PersonalStakeRepository stakeRepository;

    private final ValidPersonalStakingAmountService validPersonalStakingAmountService;

    public PersonalStakeService(Web3jUtils web3jUtils,
                                PersonalStakeRepository stakeRepository,
                                ValidPersonalStakingAmountService validPersonalStakingAmountService) {
        this.web3jUtils = web3jUtils;
        this.stakeRepository = stakeRepository;
        this.validPersonalStakingAmountService = validPersonalStakingAmountService;
    }

}
