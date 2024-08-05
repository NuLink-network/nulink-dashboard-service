package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.entity.GridStakeReward;
import com.nulink.livingratio.repository.SetLivingRatioRepository;
import com.nulink.livingratio.repository.GridStakeRewardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class SetLivingRatioService {

    private static final Object setLivingRatioTaskKey = new Object();
    private static final Object setUnLivingRatioTaskKey = new Object();
    private static boolean lockSetLivingRatioTaskFlag = false;
    private static boolean lockSetUnLivingRatioTaskFlag = false;

    private final Web3jUtils web3jUtils;
    private final SetLivingRatioRepository setLivingRatioRepository;
    private final GridStakeRewardRepository stakeRewardRepository;
    @Resource
    private RedissonClient redissonClient;

    public SetLivingRatioService(Web3jUtils web3jUtils,
                                 SetLivingRatioRepository setLivingRatioRepository,
                                 GridStakeRewardRepository stakeRewardRepository) {
        this.web3jUtils = web3jUtils;
        this.setLivingRatioRepository = setLivingRatioRepository;
        this.stakeRewardRepository = stakeRewardRepository;
    }

    @Transactional
    public void create(SetLivingRatio setLivingRatio){
        SetLivingRatio livingRatio = setLivingRatioRepository.findByEpochAndTokenId(setLivingRatio.getEpoch(), setLivingRatio.getTokenId());
        if (ObjectUtils.isNotEmpty(livingRatio)){
            setLivingRatioRepository.save(setLivingRatio);
        }
    }

    public List<SetLivingRatio> findUnset(){
        return setLivingRatioRepository.findAllBySetLivingRatioOrderByCreateTimeDesc(false);
    }

    public List<SetLivingRatio> findByEpoch(String epoch){
        return setLivingRatioRepository.findByEpoch(epoch);
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void setUnLivingRatio(){
        synchronized (setUnLivingRatioTaskKey) {
            if (SetLivingRatioService.lockSetUnLivingRatioTaskFlag) {
                log.warn("The un set living ratio task is already in progress");
                return;
            }
            SetLivingRatioService.lockSetUnLivingRatioTaskFlag = true;
        }
        RLock setUnLivingRatioTask = redissonClient.getLock("setUnLivingRatioTask");
        try{
            if (setUnLivingRatioTask.tryLock()){
                String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
                List<SetLivingRatio> livingRatios = setLivingRatioRepository.findByEpoch(previousEpoch);
                if (livingRatios.isEmpty()){
                    List<GridStakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(previousEpoch);
                    for (GridStakeReward stakeReward : stakeRewards) {
                        SetLivingRatio setLivingRatio = new SetLivingRatio();
                        setLivingRatio.setEpoch(previousEpoch);
                        setLivingRatio.setTokenId(stakeReward.getTokenId());
                        setLivingRatio.setSetLivingRatio(false);
                        create(setLivingRatio);
                    }
                } else {
                    log.warn("The unSet living ratio task has already been executed");
                }
            }
        } catch (Exception e){
            log.error("The set unLiving ratio task has failed", e);
        } finally {
            SetLivingRatioService.lockSetUnLivingRatioTaskFlag = false;
            setUnLivingRatioTask.unlock();
        }
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void setLivingRatio(){
        synchronized (setLivingRatioTaskKey) {
            if (SetLivingRatioService.lockSetLivingRatioTaskFlag) {
                log.warn("The set staking reward task is already in progress");
                return;
            }
            SetLivingRatioService.lockSetLivingRatioTaskFlag = true;
        }
        RLock setLivingRatioTask = redissonClient.getLock("setLivingRatioTask");
        try {
            if (setLivingRatioTask.tryLock()){
                log.info("The set lstaking reward task is starting ...");
                SetLivingRatio setLivingRatio = setLivingRatioRepository.findFirstBySetLivingRatioAndTransactionFailOrderById(false, false);
                if (null != setLivingRatio) {
                    String epoch = setLivingRatio.getEpoch();
                    String tokenId = setLivingRatio.getTokenId();
                    GridStakeReward gridStakeReward = stakeRewardRepository.findByEpochAndTokenId(epoch, tokenId);
                    String txHash;
                    int j = 0;
                    do {
                        txHash = web3jUtils.setStakingReward(epoch, tokenId, gridStakeReward.getStakingReward(), gridStakeReward.getValidStakingAmount());
                        j++;
                        try {
                            TimeUnit.MILLISECONDS.sleep(10000);
                        } catch (InterruptedException e) {
                            SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                            return;
                        }
                    } while (j < 20 && (null == txHash || txHash.isEmpty()));
                    try {
                        TransactionReceipt txReceipt = web3jUtils.waitForTransactionReceipt(txHash);
                        // If status in response equals 1 the transaction was successful. If it is equals 0 the transaction was reverted by EVM.
                        if (Integer.parseInt(txReceipt.getStatus().substring(2), 16) == 0) {
                            log.error("==========>set staking reward failed txHash {} revert reason: {}", txHash, txReceipt.getRevertReason());
                            setLivingRatio.setTransactionFail(true);
                            setLivingRatio.setTxHash(txHash);
                            setLivingRatio.setReason(txReceipt.getRevertReason());
                            setLivingRatioRepository.save(setLivingRatio);
                            SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                            return;
                        }
                    } catch (TransactionException exception) {
                        if (StringUtils.contains(exception.toString(), "Transaction receipt was not generated after")) {
                            setLivingRatio.setTransactionFail(true);
                            setLivingRatio.setTxHash(txHash);
                            setLivingRatio.setReason(exception.toString());
                            setLivingRatioRepository.save(setLivingRatio);
                            SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                            return;
                        }
                    }
                    setLivingRatio.setTxHash(txHash);
                    setLivingRatio.setSetLivingRatio(true);
                    setLivingRatioRepository.save(setLivingRatio);
                }
            }
        } catch (Exception e){
            log.error("==========>set staking reward Task failed reason:", e);
        } finally {
            setLivingRatioTask.unlock();
            SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
        }
    }
}
