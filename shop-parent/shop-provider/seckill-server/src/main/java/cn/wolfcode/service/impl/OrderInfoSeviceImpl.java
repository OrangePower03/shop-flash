package cn.wolfcode.service.impl;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.utils.AssertUtils;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.feign.PaymentFeignApi;
import cn.wolfcode.lock.StockLock;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode
 */
@Slf4j
@Service
@CacheConfig(cacheNames = "order_info")
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private StockLock stockLock;
    @Autowired
    private PaymentFeignApi paymentFeignApi;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;

    @Override
    public OrderInfo selectByUserIdAndSeckillId(Long userId, Long seckillId, Integer time) {
        return orderInfoMapper.selectByUserIdAndSeckillId(userId, seckillId, time);
    }

//    @Transactional(rollbackFor = Exception.class)
//    @Override
//    public String doSeckill(UserInfo userInfo, SeckillProductVo vo) {
//        // 1. 扣除秒杀商品库存
//        seckillProductService.decrStockCount(vo.getId());
//        // 2. 创建秒杀订单并保存
//        OrderInfo orderInfo = this.buildOrderInfo(userInfo, vo);
//        orderInfoMapper.insert(orderInfo);
//        // 3. 返回订单编号
//        return orderInfo.getOrderNo();
//    }


    // 需要操控数据库，待优化，优化后的结果在下面
    @Override
    @Transactional
    public String doSeckill(UserInfo userInfo, SeckillProductVo vo) {
        // 扣减库存
        seckillProductService.decrStockCount(vo.getId());

        // 生成订单
        OrderInfo orderInfo = buildOrderInfo(userInfo.getPhone(), vo);

        // 保存订单
        orderInfoMapper.insert(orderInfo);

        return orderInfo.getOrderNo();
    }

    @Override
    @Transactional
    public String doSeckill(Long userPhone, Long seckillProductId, Integer time) {
        UserInfo userInfo = new UserInfo();
        userInfo.setPhone(userPhone);
        SeckillProductVo sp = seckillProductService.selectByIdAndTime(seckillProductId, time);

        // 扣减库存
        seckillProductService.decrStockCount(sp.getId());

        // 生成订单
        OrderInfo orderInfo = buildOrderInfo(userInfo.getPhone(), sp);

        // 保存订单
        orderInfoMapper.insert(orderInfo);

        return orderInfo.getOrderNo();
    }

    private OrderInfo buildOrderInfo(Long UserPhone, SeckillProductVo vo) {
        Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(now);
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setIntergral(vo.getIntergral());
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId() + "");
        orderInfo.setPayType(OrderInfo.PAY_TYPE_ONLINE);
        orderInfo.setProductCount(1);
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(now);
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(UserPhone);
        return orderInfo;
    }

    @Override
    public OrderInfo findOrder(String orderNo) {
        return orderInfoMapper.selectById(orderNo);
    }

    @Override
    public void failRollback(OrderMessage message) {
        log.info("正在回补库存，商品id:{}", message.getSeckillId());
        String threadId = String.valueOf(IdGenerateUtil.get().nextId());

        try {
            stockLock.lock(threadId, message.getSeckillId());

            // 回补库存，这里是有脏数据的问题的，可能前脚查的商品数量是大于0的但在做++的时候商品数量就小于0了，可以考虑加锁
            String StockHashKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(message.getTime().toString());
            Long count = Long.valueOf((String) redisTemplate.opsForHash().get(StockHashKey, message.getSeckillId().toString()));
            if (count != null && count < 0) {
                redisTemplate.opsForHash().put(StockHashKey, message.getSeckillId().toString(), 1);
            } else
                redisTemplate.opsForHash().increment(StockHashKey, message.getSeckillId().toString(), 1);
            // 删除本地标识，不能直接删，会有分布式下的错误
//            OrderInfoController.delete_SELL_OUT_SHOPPING(message.getSeckillId());
            rocketMQTemplate.asyncSend(MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC, message.getSeckillId(), new DefaultSendCallback("取消本地标识"));
        }
        finally {
            stockLock.unLock(threadId, message.getSeckillId());
        }
        // 删除用户下单成功数据
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.join(message.getSeckillId().toString());
        redisTemplate.opsForHash().delete(orderHashKey, message.getUserPhone().toString());
    }

    @Override
    @Transactional
    public void checkPayTimeout(OrderMessage orderMessage) {
        // 1. 查订单对象，查看是否支付
        int row = orderInfoMapper.changePayStatus(orderMessage.getOrderNo(), OrderInfo.STATUS_TIMEOUT, OrderInfo.PAY_TYPE_ONLINE);
        // 2. 取消需要回滚库存
        if(row > 0) {
            // MySQL回滚，库存+1
            seckillProductService.increaseStock(orderMessage.getSeckillId());
            // Redis库存回滚
            failRollback(orderMessage);
        }
    }

    @Override
    public String onlinePay(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderNo);
        AssertUtils.nonNull(orderInfo, "订单不存在");
        AssertUtils.isTrue(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus()), "订单状态异常，请刷新检查订单详情");

        // 封装支付参数
        PayVo pay=new PayVo();
        pay.setBody("cq的秒杀"+orderInfo.getProductName());
        pay.setSubject(orderInfo.getProductName());
        pay.setOutTradeNo(orderNo);
        pay.setTotalAmount(String.valueOf(orderInfo.getSeckillPrice()));
        Result<String> result = paymentFeignApi.doPay(pay);
        return result.checkAndGet();
    }

    @Override
    public void alipaySuccess(PayResultVo payResult) {
        // 通过订单号查阅订单信息对象
        OrderInfo orderInfo = orderInfoMapper.selectById(payResult.getOrderNo());
        AssertUtils.nonNull(orderInfo, "订单信息有误");
        AssertUtils.isEquals(orderInfo.getSeckillPrice().toString(), payResult.getTotalAmount(), "支付金额有误");

        // 更新订单状态为支付成功，注意保证幂等性
        int row = orderInfoMapper.changePayStatus(orderInfo.getOrderNo(),OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_ONLINE);
        AssertUtils.isTrue(row > 0,"支付状态更新失败");

        // 记录支付流水
        PayLog payLog=new PayLog();
        payLog.setPayType(PayLog.PAY_TYPE_ONLINE);
        payLog.setTradeNo(payResult.getTradeNo());
        payLog.setOutTradeNo(payResult.getOrderNo());
        payLog.setTotalAmount(payResult.getTotalAmount());
        payLog.setNotifyTime(String.valueOf(System.currentTimeMillis()));
        payLogMapper.insert(payLog);
    }

    @Override
    @Transactional
    public String refund(String orderNo, Long phone, String token) {
        // 查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectById(orderNo);
        AssertUtils.nonNull(orderInfo, "该订单不存在");
        // 查询订单是否为当前用户的订单
        AssertUtils.isEquals(phone, orderInfo.getUserId(), "订单状态异常，无法退款");
        // 校验订单状态
        AssertUtils.isTrue(OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus()), "订单未支付不允许退款");

        RefundVo refund=new RefundVo(orderNo, orderInfo.getSeckillPrice().toString(), "不想要了");
        // 调用退款接口
        Result<String> result = OrderInfo.PAY_TYPE_ONLINE == orderInfo.getPayType() ?
                paymentFeignApi.alipayRefund(refund) : // 在线支付退款
                Result.success(); // 积分退款 todo
        // 退款成功后需要做的事
        AssertUtils.isTrue(!result.hasError(), "退款失败");
        // 1. 修改订单状态
        int row = orderInfoMapper.changeRefundStatus(orderNo, OrderInfo.STATUS_REFUND);
        AssertUtils.isTrue(row > 0, "退款失败");
        // 2. 记录退款流水
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(orderNo);
        refundLog.setRefundReason("用户申请退款" + orderInfo.getProductName());
        refundLog.setRefundTime(new Date());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice().toString());
        refundLog.setRefundType(orderInfo.getPayType());
        refundLogMapper.insert(refundLog);
        // 3. 回补Mysql库存
        seckillProductService.increaseStock(orderInfo.getSeckillId());
        // 4. 回补Redis库存和删除本地标识
        failRollback(new OrderMessage(orderInfo.getSeckillTime(), orderInfo.getSeckillId(), token, phone, orderNo));
        return "退款成功";
    }
}


