package cn.wolfcode.mq.listener;

import cn.wolfcode.core.WebsocketServer;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.websocket.Session;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQConstant.ORDER_RESULT_TOPIC,
        consumerGroup = MQConstant.ORDER_RESULT_CONSUMER_GROUP
)
public class OrderResultMessageListener implements RocketMQListener<OrderMQResult> {
    @Override
    public void onMessage(OrderMQResult result) {
        String json = JSON.toJSONString(result);
        log.info("[订单结果] 接收到订单结果消息: {}", json);
        Session session = WebsocketServer.SESSION_MAP.get(result.getToken());
        try {
            int count=0;
            do {
                if (session != null) {
                    // 发送消息给客户端
                    session.getBasicRemote().sendText(json);
                    break;
                }
                // 没拿到session可能是前端没创建session对象导致，睡一会再拿
                log.info("[订单结果] 未拿到session，等待500ms，第{}次重试", count);
                Thread.sleep(500);

            } while(count++ < 3);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
