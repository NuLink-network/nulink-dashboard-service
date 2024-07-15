package com.nulink.livingratio.config;

import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String redisIP;

    @Value("${spring.redis.password:}")
    private String redisPwd;

    @Value("${spring.redis.port}")
    private String redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig sConfig = config.useSingleServer().setAddress(String.format("redis://%s:%s/0", redisIP, redisPort));
        if(StringUtils.isNotBlank(redisPwd))
        {
            sConfig.setPassword(redisPwd);
        }
        return Redisson.create(config);
    }
}

