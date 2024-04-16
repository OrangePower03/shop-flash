package cn.wolfcode.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;

@Slf4j
public class DefaultSendCallback implements SendCallback {
    private final String tag;

    public DefaultSendCallback(String tag) {
        this.tag = tag;
    }

    @Override
    public void onSuccess(SendResult sendResult) {
        log.info("[{}] 消息发送成功，消息id为：{}", tag, sendResult.getMsgId());
    }

    @Override
    public void onException(Throwable t) {
        log.warn("[{}] 消息发送成功，异常信息为：{}", tag, t.getMessage());
    }
}
