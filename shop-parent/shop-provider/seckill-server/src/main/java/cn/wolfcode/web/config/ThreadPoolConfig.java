package cn.wolfcode.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ScheduledExecutorService watchDogScheduledExecutor() {
        return new ScheduledThreadPoolExecutor(6);
    }
}
