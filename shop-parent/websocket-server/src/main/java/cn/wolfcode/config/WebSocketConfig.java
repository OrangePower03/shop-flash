package cn.wolfcode.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        // 自动注册使用@ServerEndpoint注解声明的Websocket endpoint
        return new ServerEndpointExporter();
    }
}
