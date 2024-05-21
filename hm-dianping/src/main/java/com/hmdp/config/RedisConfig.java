package com.hmdp.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedisConfig {
    private String host;
    private String port;
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config = new Config();
        //添加redis地址，这里添加了单点地址，可以使用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://"+host+":"+port)
                .setPassword(password);
        return Redisson.create(config);
    }
}
