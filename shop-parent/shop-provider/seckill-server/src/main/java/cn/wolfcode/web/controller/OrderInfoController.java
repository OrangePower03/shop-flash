package cn.wolfcode.web.controller;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.utils.AssertUtils;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.common.web.resolver.RequestUser;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    /**
     * 售罄的商品id集合
     */
    private static final Set<Long> SELL_OUT_SHOPPING =new ConcurrentHashSet<>();

    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    public static void delete_SELL_OUT_SHOPPING(Long seckillId) {
        SELL_OUT_SHOPPING.remove(seckillId);
    }

    /**
     * 优化前：
     *  测试数据：500 个用户，100 线程，执行 50 次
     *  测试情况：330 QPS
     */
//    @RequireLogin
//    @RequestMapping("/doSeckill")
//    public Result<String> doSeckill(Integer time, Long seckillId, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
//        // 1. 基于 token 获取到用户信息(必须登录)
//        UserInfo userInfo = this.getUserByToken(token);
//        // 2. 基于场次+秒杀id获取到秒杀商品对象
//        SeckillProductVo vo = seckillProductService.selectByIdAndTime(seckillId, time);
//        if (vo == null) {
//            throw new BusinessException(SeckillCodeMsg.REMOTE_DATA_ERROR);
//        }
//        // 3. 判断时间是否大于开始时间 && 小于 开始时间+2小时
//        /*if (!DateUtil.isLegalTime(vo.getStartDate(), time)) {
//            throw new BusinessException(SeckillCodeMsg.OUT_OF_SECKILL_TIME_ERROR);
//        }*/
//        // 4. 判断用户是否重复下单
//        // 基于用户 + 秒杀 id + 场次查询订单, 如果存在订单, 说明用户已经下过单
//        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSeckillId(userInfo.getPhone(), seckillId, time);
//        if (orderInfo != null) {
//            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
//        }
//        // 5. 判断库存是否充足
//        if (vo.getStockCount() <= 0) {
//            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
//        }
//        // 6. 执行下单操作(减少库存, 创建订单)
//        String orderNo = orderInfoService.doSeckill(userInfo, vo);
//        return Result.success(orderNo);
//    }



    @RequireLogin
    @GetMapping("/find")
    public Result<OrderInfo> findOrderByOrderNo(String orderNo, @RequestUser UserInfo userInfo) {
        if(!orderInfoService.findOrder(orderNo).getUserId().equals(userInfo.getPhone())) {
            return Result.error(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        return Result.success(orderInfoService.findOrder(orderNo));
    }

    @RequireLogin
    @PostMapping("/doSeckill")
    public Result<String> doSeckill(Long seckillId, Integer time, @RequestUser UserInfo userInfo, @RequestHeader String token) {
        if(SELL_OUT_SHOPPING.contains(seckillId))
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);

        // 1. 根据token获取用户信息。这个封装在了一个参数解析器中了

        // 2. 根据秒杀id和场次获取秒杀商品对象
        SeckillProductVo sp = seckillProductService.selectByIdAndTime(seckillId, time);
        if(sp == null)
            return Result.error(SeckillCodeMsg.NOT_FOUND_ERROR);

        // 3. 查看商品是否在场次时间范围内
        if(!isBetweenSeckillTime(sp))
            return Result.error(SeckillCodeMsg.OUT_OF_SECKILL_TIME_ERROR);

        // 4. 查看用户是否已下单过
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.join(seckillId.toString());
        Boolean absent = redisTemplate.opsForHash().putIfAbsent(orderHashKey, userInfo.getPhone().toString(), "1");
        // 这样也可以实现，并且这个还可以控制下单次数
//        Long count = redisTemplate.opsForHash().increment(orderHashKey, userInfo.getPhone(), 1);
//        AssertUtils.isTrue(count <= 1, "请勿重复下单");
//        OrderInfo orderInfo = orderInfoService.selectByUserIdAndSeckillId(userInfo.getPhone(), seckillId, time);
        if(!absent)
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);

        try {
            // 5. 查看商品库存是否充足
            String StockHashKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time.toString());
            Long remain = redisTemplate.opsForHash().increment(StockHashKey, seckillId.toString(), -1);
            AssertUtils.isTrue(remain >= 0, "秒杀商品库存不足");
//        AssertUtils.isTrue(sp.getStockCount() > 1, "秒杀商品库存不足");

            // 6. 生成订单，扣减库存，这个需要操作Mysql，性能低下
//            return Result.success(orderInfoService.doSeckill(userInfo, sp));
            // 6. 发送MQ消息，异步扣减库存
            rocketMQTemplate.asyncSend(
                    MQConstant.ORDER_PENDING_TOPIC,
                    new OrderMessage(time, sp.getId(), token, userInfo.getPhone(), ""),
                    new DefaultSendCallback("创建订单"));
            return Result.success("正在努力的创建订单中");
        }
        catch(BusinessException e) {
            // 避免因为库存不足导致的下单失败再次下单时显示重复下单意思
            redisTemplate.opsForHash().delete(orderHashKey, userInfo.getPhone().toString());
            if(!SeckillCodeMsg.SYSTEM_BUSY.equals(e.getCodeMsg()))
                SELL_OUT_SHOPPING.add(seckillId); // 记录在服务器的内存中
            return Result.error(e.getCodeMsg());
        }
    }

    // 判断时间
    private boolean isBetweenSeckillTime(SeckillProductVo sp) {
        return true;
//        Calendar instance = Calendar.getInstance();
//        instance.setTime(sp.getStartDate());
//        // 设置小时
//        instance.set(Calendar.HOUR_OF_DAY, sp.getTime());
//        // 开始时间
//        Date startDate = instance.getTime();
//        // 结束时间
//        instance.add(Calendar.HOUR_OF_DAY, 2);
//        Date endDate = instance.getTime();
//
//        long now = System.currentTimeMillis();
//        return now >= startDate.getTime() && now < endDate.getTime();
    }

//    private UserInfo getUserByToken(String token) {
//        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
//    }
}
