package cn.wolfcode.mq.listen;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.web.controller.OrderInfoController;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC,
        consumerGroup = MQConstant.CANCEL_SECKILL_OVER_SIGN_CONSUMER_GROUP,
        messageModel = MessageModel.BROADCASTING // 广播模式推送
)
public class CancelStockOverFlagMessageListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String seckillId) {
        log.info("[取消本地标识]，收到消息，准备删除的商品id：{}", seckillId);
        //
        OrderInfoController.delete_SELL_OUT_SHOPPING(Long.valueOf(seckillId));
    }
}
