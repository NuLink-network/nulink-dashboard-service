package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.NodePoolEvents;
import com.nulink.livingratio.entity.event.StakingEvent;
import com.nulink.livingratio.entity.ValidPersonalStakingAmount;
import com.nulink.livingratio.repository.ValidPersonalStakingAmountRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ValidPersonalStakingAmountService {

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

    private final ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository;

    public ValidPersonalStakingAmountService(ValidPersonalStakingAmountRepository validPersonalStakingAmountRepository) {
        this.validPersonalStakingAmountRepository = validPersonalStakingAmountRepository;
    }

    @Transactional
    public void updateValidPersonalStakingAmount(NodePoolEvents staking){
        String stakeUser = staking.getUser();
        String epoch =  staking.getEpoch();
        String tokenId = staking.getTokenId();
        String event = staking.getEvent();

        // Get the last record in tokenId and userAddress
        ValidPersonalStakingAmount personalStakingAmount =
                validPersonalStakingAmountRepository.findFirstByTokenIdAndUserAddressOrderByCreateTimeDesc(tokenId, stakeUser);
        if (null != personalStakingAmount){
            if (epoch.equalsIgnoreCase(String.valueOf(personalStakingAmount.getEpoch()))){
                if (STAKE_EVENT.equalsIgnoreCase(event)){
                    personalStakingAmount.setStakingAmount(new BigDecimal(personalStakingAmount.getStakingAmount()).add(new BigDecimal(staking.getAmount())).toString());
                }
            } else {
                personalStakingAmount = new ValidPersonalStakingAmount();
                personalStakingAmount.setUserAddress(stakeUser);
                personalStakingAmount.setTokenId(tokenId);
                if (STAKE_EVENT.equalsIgnoreCase(event)){
                    personalStakingAmount.setStakingAmount(new BigDecimal(personalStakingAmount.getStakingAmount()).add(new BigDecimal(staking.getAmount())).toString());
                }
            }

        } else {
            personalStakingAmount = new ValidPersonalStakingAmount();
            personalStakingAmount.setUserAddress(stakeUser);
            personalStakingAmount.setTokenId(tokenId);
            if (STAKE_EVENT.equalsIgnoreCase(event)){
                personalStakingAmount.setStakingAmount(staking.getAmount());
            }
        }
        if (UN_STAKE_EVENT.equalsIgnoreCase(event)){
            personalStakingAmount.setStakingAmount("0");
        }
        personalStakingAmount.setEpoch(Integer.parseInt(epoch));
        personalStakingAmount.setTxHash(staking.getTxHash());
        validPersonalStakingAmountRepository.save(personalStakingAmount);
    }

    public ValidPersonalStakingAmount findAllByTokenIdAndUserAddress(String tokenId, String userAddress){
        return validPersonalStakingAmountRepository.findFirstByTokenIdAndUserAddressOrderByCreateTimeDesc(tokenId, userAddress);
    }

    public List<ValidPersonalStakingAmount> findAllByTokenId(String tokenId){
        return validPersonalStakingAmountRepository.findAllByTokenId(tokenId);
    }
}
