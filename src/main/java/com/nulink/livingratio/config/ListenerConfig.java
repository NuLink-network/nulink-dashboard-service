package com.nulink.livingratio.config;

import com.nulink.livingratio.contract.event.listener.impl.BlockEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ListenerConfig {

    @Bean
    public BlockEventListener blockEventListenerDelay0() {
        return new BlockEventListener();
    }

    @Bean
    public BlockEventListener blockEventDelayListener30() {
        return new BlockEventListener();
    }

    @Bean
    public BlockEventListener blockEventDelayListener60() {
        return new BlockEventListener();
    }
}
