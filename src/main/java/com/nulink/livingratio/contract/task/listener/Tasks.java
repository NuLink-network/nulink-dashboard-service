package com.nulink.livingratio.contract.task.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.nulink.livingratio.contract.event.listener.impl.BlockEventListener;

import java.util.HashSet;
import java.util.Set;

@Component
public class Tasks {

    public static Logger logger = LoggerFactory.getLogger(Tasks.class);

    private static final Object blockListenerTaskKey = new Object();
    private static boolean lockBlockListenerTaskFlag = false;

    private static final Object blockListenerDelay30TaskKey = new Object();
    private static boolean lockBlockListenerDelay30TaskFlag = false;

    private static final Object blockListenerDelay60TaskKey = new Object();
    private static boolean lockBlockListenerDelay60TaskFlag = false;

    @Autowired
    BlockEventListener blockEventListener;

    @Autowired
    BlockEventListener blockEventDelayListener30;

    @Autowired
    BlockEventListener blockEventDelayListener60;

    @Async
    @Scheduled(cron = "0/5 * * * * ?")
    public void scanBlockEvent() {

        synchronized (blockListenerTaskKey) {
            if (Tasks.lockBlockListenerTaskFlag) {
                logger.warn("The blockchain event scanning task is already in progress");
                return;
            }
            Tasks.lockBlockListenerTaskFlag = true;
        }

        logger.info("Commence the execution of the blockchain event scanning task.");
        try {
            blockEventListener.start(0, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerTaskFlag = false;

        logger.info("The Delay0 blockchain event scanning task has concluded.");
    }

    @Async
    @Scheduled(cron = "0/6 * * * * ?")
    public void scanBlockEventDelay30() {

        synchronized (blockListenerDelay30TaskKey) {
            if (Tasks.lockBlockListenerDelay30TaskFlag) {
                logger.warn("The Delay30 blockchain event scanning task is currently in progress.");
                return;
            }
            Tasks.lockBlockListenerDelay30TaskFlag = true;
        }

        logger.info("Initiate the execution of the Delay50 blockchain event scanning task.");
        try {

            blockEventDelayListener30.start(30, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerDelay30TaskFlag = false;

        logger.info("The Delay30 blockchain event scanning task has concluded.");
    }

    @Async
    @Scheduled(cron = "0/7 * * * * ?")
    public void scanBlockEventDelay60() {

        synchronized (blockListenerDelay60TaskKey) {
            if (Tasks.lockBlockListenerDelay60TaskFlag) {
                logger.warn("The Delay60 blockchain event scanning task is currently in progress.");
                return;
            }
            Tasks.lockBlockListenerDelay60TaskFlag = true;
        }

        logger.info("Initiate the execution of the Delay60 blockchain event scanning task.");
        try {
            Set<String> disableTaskNames = new HashSet<>();
            disableTaskNames.add("NodePoolStakingManager");
            disableTaskNames.add("NodePoolFactory");
            blockEventDelayListener60.start(60, null, disableTaskNames);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerDelay60TaskFlag = false;

        logger.info("The Delay60 blockchain event scanning task has concluded.");
    }

}
