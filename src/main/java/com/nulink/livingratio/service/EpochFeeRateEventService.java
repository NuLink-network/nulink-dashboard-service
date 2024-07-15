package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.event.CreateNodePoolEvent;
import com.nulink.livingratio.entity.event.EpochFeeRateEvent;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.repository.CreateNodePoolEventRepository;
import com.nulink.livingratio.repository.EpochFeeRateEventRepository;
import com.nulink.livingratio.repository.GridStakeRewardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import org.springframework.stereotype.Service;

@Service
public class EpochFeeRateEventService {

    private final Web3jUtils web3jUtils;

    private final EpochFeeRateEventRepository epochFeeRateEventRepository;

    private final GridStakeRewardRepository gridStakeRewardRepository;

    private final CreateNodePoolEventRepository createNodePoolEventRepository;

    public EpochFeeRateEventService(Web3jUtils web3jUtils,
                                    EpochFeeRateEventRepository epochFeeRateEventRepository,
                                    GridStakeRewardRepository gridStakeRewardRepository,
                                    CreateNodePoolEventRepository createNodePoolEventRepository) {
        this.web3jUtils = web3jUtils;
        this.epochFeeRateEventRepository = epochFeeRateEventRepository;
        this.gridStakeRewardRepository = gridStakeRewardRepository;
        this.createNodePoolEventRepository = createNodePoolEventRepository;
    }

    public void save(EpochFeeRateEvent epochFeeRateEvent) {
        epochFeeRateEventRepository.save(epochFeeRateEvent);
    }

    public EpochFeeRateEvent findByTxHash(String txHash) {
        return epochFeeRateEventRepository.findByTxHash(txHash);
    }

    public EpochFeeRateEvent findByEpochAndTokenId(String epoch, String tokenId) {
        return epochFeeRateEventRepository.findByEpochAndTokenId(epoch, tokenId);
    }

    public String getCurrentFeeRate(String tokenId) {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        EpochFeeRateEvent epochFeeRateEvent = epochFeeRateEventRepository.findByEpochAndTokenId(currentEpoch, tokenId);
        if (epochFeeRateEvent == null) {
            GridStakeReward gridStakeReward = gridStakeRewardRepository.findByEpochAndTokenId(String.valueOf(Integer.parseInt(currentEpoch) - 1), tokenId);
            if (gridStakeReward == null) {
                CreateNodePoolEvent pool = createNodePoolEventRepository.findByTokenId(tokenId);
                return pool.getFeeRatio();
            } else {
                return gridStakeReward.getCurrentFeeRatio();
            }
        } else {
            return epochFeeRateEvent.getFeeRate();
        }
    }

    public String getNextFeeRate(String tokenId) {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        Integer nextEpoch = Integer.parseInt(currentEpoch) + 1;
        EpochFeeRateEvent epochFeeRateEvent = epochFeeRateEventRepository.findByEpochAndTokenId(String.valueOf(nextEpoch), tokenId);
        if (epochFeeRateEvent == null) {
            return getCurrentFeeRate(tokenId);
        } else {
            return epochFeeRateEvent.getFeeRate();
        }
    }
}
