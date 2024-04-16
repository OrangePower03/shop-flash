package com.example.quartzServer.config;

import com.example.quartzServer.job.SeckillProductInitJob;
import com.example.quartzServer.job.UserCacheJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessJobConfig {
    public static final String SECKILL_PRODUCT_INIT_JOB_GROUP = "seckillProductInitJobGroup";
    public static final String USER_CACHE_JOB_GROUP = "userCacheGroup";

    @Value("${job.seckillProduct.cron}")
    private String seckillProductCron;
    @Value("${job.userCache.cron}")
    private String userCacheCron;

    @Bean
    public JobDetail userCacheJobDetail() {
        return JobBuilder.newJob(UserCacheJob.class)
                .withIdentity("userCacheJob",USER_CACHE_JOB_GROUP)
                .requestRecovery()
                .storeDurably(true)
                .build();
    }

    @Bean JobDetail seckillProductInitJobDetail() {
        return JobBuilder.newJob(SeckillProductInitJob.class)
                .withIdentity("seckillProductInitJob",SECKILL_PRODUCT_INIT_JOB_GROUP)
                .requestRecovery()
                .storeDurably(true)
                .build();
    }

    @Bean
    public Trigger initSeckillProductListJob() {
        return TriggerBuilder.newTrigger()
                .withIdentity("seckillProductInitTrigger",SECKILL_PRODUCT_INIT_JOB_GROUP)
                .forJob(seckillProductInitJobDetail())
                .withSchedule(CronScheduleBuilder.cronSchedule(seckillProductCron))
                .startNow()
                .build();
    }

    @Bean
    public Trigger testSeckillProductListJob() {
        return TriggerBuilder.newTrigger()
                .withIdentity("seckillProductInitTrigger",SECKILL_PRODUCT_INIT_JOB_GROUP)
                .withDescription("启动项目时立即执行一次")
                .forJob(seckillProductInitJobDetail())
                .startNow()
                .build();
    }

    @Bean
    public Trigger initUserCacheJob() {
        return TriggerBuilder.newTrigger()
                .withIdentity("userCacheTrigger",USER_CACHE_JOB_GROUP)
                .forJob(userCacheJobDetail())
                .withSchedule(CronScheduleBuilder.cronSchedule(userCacheCron))
                .startNow()
                .build();
    }
}
