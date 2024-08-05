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

    private static final Object blockListenerDelay50TaskKey = new Object();
    private static boolean lockBlockListenerDelay50TaskFlag = false;

    private static final Object blockListenerDelay100TaskKey = new Object();
    private static boolean lockBlockListenerDelay100TaskFlag = false;

    @Autowired
    BlockEventListener blockEventListener;

    @Autowired
    BlockEventListener blockEventDelayListener50;

    @Autowired
    BlockEventListener blockEventDelayListener100;

    @Async
    @Scheduled(cron = "0/6 * * * * ?")
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
    public void scanBlockEventDelay50() {

        synchronized (blockListenerDelay50TaskKey) {
            if (Tasks.lockBlockListenerDelay50TaskFlag) {
                logger.warn("The Delay50 blockchain event scanning task is currently in progress.");
                return;
            }
            Tasks.lockBlockListenerDelay50TaskFlag = true;
        }

        logger.info("Initiate the execution of the Delay50 blockchain event scanning task.");
        try {

            blockEventDelayListener50.start(50, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerDelay50TaskFlag = false;

        logger.info("The Delay50 blockchain event scanning task has concluded.");
    }

    @Async
    @Scheduled(cron = "0/6 * * * * ?")
    public void scanBlockEventDelay100() {

        synchronized (blockListenerDelay100TaskKey) {
            if (Tasks.lockBlockListenerDelay100TaskFlag) {
                logger.warn("The Delay100 blockchain event scanning task is currently in progress.");
                return;
            }
            Tasks.lockBlockListenerDelay100TaskFlag = true;
        }

        logger.info("Initiate the execution of the Delay100 blockchain event scanning task.");
        try {
            Set<String> disableTaskNames = new HashSet<>();
            disableTaskNames.add("NodePoolStakingManager");
            disableTaskNames.add("NodePoolFactory");
            blockEventDelayListener100.start(100, null, disableTaskNames);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerDelay100TaskFlag = false;

        logger.info("The Delay100 blockchain event scanning task has concluded.");
    }

}
