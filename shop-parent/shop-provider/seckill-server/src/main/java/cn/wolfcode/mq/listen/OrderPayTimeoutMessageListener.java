package cn.wolfcode.mq.listen;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.impl.OrderInfoSeviceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC,
        consumerGroup = MQConstant.ORDER_PAY_TIMEOUT_CONSUMER_GROUP
)
public class OrderPayTimeoutMessageListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(OrderMessage orderMessage) {
        log.info("[订单超时取消] 收到订单消息，正在查看订单的支付状态，订单号为：{}", orderMessage.getOrderNo());
        orderInfoService.checkPayTimeout(orderMessage);
    }
}
