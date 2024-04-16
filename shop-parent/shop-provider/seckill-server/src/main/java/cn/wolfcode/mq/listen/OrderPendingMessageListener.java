package cn.wolfcode.mq.listen;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.mq.DefaultSendCallback;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
  	topic = MQConstant.ORDER_PENDING_TOPIC,
    consumerGroup = MQConstant.ORDER_PENDING_CONSUMER_GROUP
)
public class OrderPendingMessageListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(OrderMessage message) {
        log.info("[创建订单] 收到创建订单消息，订单数据：{}", JSON.toJSONString(message));
        OrderMQResult result=new OrderMQResult();
        result.setToken(message.getToken());
        try {
            String orderNo = orderInfoService.doSeckill(message.getUserPhone(), message.getSeckillId(), message.getTime());
            result.setOrderNo(orderNo);
            result.setCode(Result.SUCCESS_CODE);
            result.setMsg("订单创建成功");
            // 发送延迟消息检查订单超时
            message.setOrderNo(orderNo);
            Message<OrderMessage> msg = MessageBuilder.withPayload(message).build();
            rocketMQTemplate.asyncSend(MQConstant.ORDER_PAY_TIMEOUT_TOPIC, msg, new DefaultSendCallback("订单超时取消"), 2000, MQConstant.ORDER_PAY_TIMEOUT_DELAY_LEVEL);
        }
        catch(Exception e) {
            // 订单创建失败，需要回补数据和删除用户下单的数据
            orderInfoService.failRollback(message);
            result.setCode(SeckillCodeMsg.SECKILL_ERROR.getCode());
            result.setMsg(SeckillCodeMsg.SECKILL_ERROR.getMsg());
        }
        rocketMQTemplate.asyncSend(
                MQConstant.ORDER_RESULT_TOPIC,
                result,
                new DefaultSendCallback("订单结果")
        );

    }
}
