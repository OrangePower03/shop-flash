package com.example.quartzServer.job;

import com.example.quartzServer.enums.JobRedisKey;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Set;

/**
 * 用于处理用户缓存的定时任务
 * 为了保证Redis中的内存的有效使用。
 * 我们默认保留7天内的用户缓存数据，每天凌晨的时候会把7天前的用户登录缓存数据删除掉
 */
@Component
@Setter
@Getter
@RefreshScope
@Slf4j
public class UserCacheJob implements Job {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void execute(JobExecutionContext shardingContext) {
        doWork();
    }

    private void doWork() {
        // 获取日历对象
        Calendar calendar = Calendar.getInstance();
        // 在当前时间天数的基础上做加法操作
        // 7天前 = 当前天 - 7
        calendar.add(Calendar.DATE, -7);
        //获取7天前的日期
        Long max = calendar.getTime().getTime();
        String userZSetKey = JobRedisKey.USER_ZSET.join("");
        String userHashKey = JobRedisKey.USER_HASH.join("");
        Set<String> ids = redisTemplate.opsForZSet().rangeByScore(userZSetKey, 0, max);
        //删除7天前的用户缓存数据
        if (ids.size() > 0) {
            redisTemplate.opsForHash().delete(userHashKey, ids.toArray());
            redisTemplate.opsForZSet().removeRangeByScore(userZSetKey, 0, max);
        }
    }


}
