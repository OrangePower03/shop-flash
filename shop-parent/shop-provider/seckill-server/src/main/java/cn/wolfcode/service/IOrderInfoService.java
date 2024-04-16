package cn.wolfcode.service;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayResultVo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.OrderMessage;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    OrderInfo selectByUserIdAndSeckillId(Long phone, Long seckillId, Integer time);

    String doSeckill(UserInfo userInfo, SeckillProductVo vo);

    String doSeckill(Long userPhone, Long seckillProductId, Integer time);

    OrderInfo findOrder(String orderNo);

    void failRollback(OrderMessage message);

    void checkPayTimeout(OrderMessage orderMessage);

    String onlinePay(String orderNo);

    void alipaySuccess(PayResultVo payResult);

    String refund(String orderNo, Long phone, String token);
}
