package cn.wolfcode.lock;

import cn.wolfcode.common.utils.AssertUtils;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StockLock {
    private final double WATCH_DOG_DELAY_TIME_RATE=0.8;
    private final Map<Long, Future<?>> WATCH_DOG_MAP=new ConcurrentHashMap<>();

    @Autowired
    private ScheduledExecutorService watchDogScheduledExecutor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeckillProductMapper seckillProductMapper;

    public void lock(String threadId, Long seckillId) {
        String lockKey = "seckill_product_lock:" + seckillId;
        // 获取分布式下的全局id
        int timeout=10;
        int count=0;
        Future<?> watchDog = null;

        while(count++ <= 5) {
            // 这里无法保证原子性的说，可以使用Lua脚本来优化此处，现在暂时不做处理
            Boolean isSet = redisTemplate.opsForValue().setIfAbsent(lockKey, threadId);
            if (isSet != null && isSet) {
                redisTemplate.expire(lockKey, timeout, TimeUnit.SECONDS);
                break;
            }
        }
        AssertUtils.isTrue(count <= 5, SeckillCodeMsg.SYSTEM_BUSY);

        int stockCount = seckillProductMapper.getStockCount(seckillId);
        System.out.println("当前库存"+stockCount);
        AssertUtils.isTrue(stockCount > 0, SeckillCodeMsg.SECKILL_STOCK_OVER);

        // 这个线程池下中断线程是没用的，应该将任务取消
        watchDog = watchDogScheduledExecutor.scheduleAtFixedRate(
                () -> {
                    log.info("锁正在续期...");
                    // 查询key是否存在，存在表示业务没执行完
                    String value = redisTemplate.opsForValue().get(lockKey);
                    if(threadId.equals(value)) {
                        redisTemplate.expire(lockKey, (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),TimeUnit.SECONDS);
                    }
                    // 不存在key，业务已经执行完毕，这部分的代码不用在这写，应该在业务完成的地方
                }
                ,
                (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),
                (long) (timeout * WATCH_DOG_DELAY_TIME_RATE),
                TimeUnit.SECONDS
        );
        WATCH_DOG_MAP.put(seckillId, watchDog);
    }

    public void unLock(String threadId, Long stockId) {
        log.info("业务完成，执行释放锁操作");
        String lockKey = "seckill_product_lock:" + stockId;
        // 释放自己的锁，避免释放其他线程的锁
        String value = redisTemplate.opsForValue().get(lockKey);
        if(threadId.equals(value)) {
            Future<?> watchDog = WATCH_DOG_MAP.get(stockId);
            if(watchDog != null)
                watchDog.cancel(true);
            redisTemplate.delete(lockKey);
        }
    }
}
