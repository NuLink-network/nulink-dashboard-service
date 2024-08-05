package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.event.EpochFeeRateEvent;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.repository.EpochFeeRateEventRepository;
import com.nulink.livingratio.repository.GridStakeRewardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EpochFeeRateEventService {

    private final Web3jUtils web3jUtils;

    private final EpochFeeRateEventRepository epochFeeRateEventRepository;

    private final GridStakeRewardRepository gridStakeRewardRepository;

    public EpochFeeRateEventService(Web3jUtils web3jUtils,
                                    EpochFeeRateEventRepository epochFeeRateEventRepository,
                                    GridStakeRewardRepository gridStakeRewardRepository) {
        this.web3jUtils = web3jUtils;
        this.epochFeeRateEventRepository = epochFeeRateEventRepository;
        this.gridStakeRewardRepository = gridStakeRewardRepository;
    }

    public void save(EpochFeeRateEvent epochFeeRateEvent) {
        EpochFeeRateEvent feeRateEvent = epochFeeRateEventRepository.findByTxHash(epochFeeRateEvent.getTxHash());
        if (feeRateEvent != null){
            return;
        }
        epochFeeRateEventRepository.save(epochFeeRateEvent);
    }

    public EpochFeeRateEvent findByTxHash(String txHash) {
        return epochFeeRateEventRepository.findByTxHash(txHash);
    }

    public EpochFeeRateEvent findByEpochAndTokenId(String epoch, String tokenId) {
        return epochFeeRateEventRepository.findByEpochAndTokenId(epoch, tokenId);
    }

    public List<EpochFeeRateEvent> findAllByEpoch(String epoch) {
        return epochFeeRateEventRepository.findAllByEpoch(epoch);
    }

    public String getFeeRate(String tokenId, String epoch) {
        EpochFeeRateEvent epochFeeRateEvent = epochFeeRateEventRepository.findFirstByEpochAndTokenIdOrderByCreateTimeDesc(epoch, tokenId);
        if (epochFeeRateEvent != null){
            return epochFeeRateEvent.getFeeRate();
        } else {
            return "";
        }
    }

    /*public String getNextFeeRate(String tokenId) {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        Integer nextEpoch = Integer.parseInt(currentEpoch) + 1;
        EpochFeeRateEvent epochFeeRateEvent = epochFeeRateEventRepository.findFirstByEpochAndTokenIdOrderByCreateTimeDesc(String.valueOf(nextEpoch), tokenId);
        if (epochFeeRateEvent == null) {
            return getCurrentFeeRate(tokenId);
        } else {
            return epochFeeRateEvent.getFeeRate();
        }
    }*/
}
