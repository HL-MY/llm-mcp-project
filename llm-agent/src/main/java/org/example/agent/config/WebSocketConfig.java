package org.example.agent.config;

import org.example.agent.controller.DirectChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket // 【关键修改】启用 MVC WebSocket 支持
public class WebSocketConfig implements WebSocketConfigurer {

    private final DirectChatWebSocketHandler chatHandler;

    public WebSocketConfig(DirectChatWebSocketHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 【关键修改】使用 MVC 方式注册 Handler，Tomcat 可以识别
        registry.addHandler(chatHandler, "/ws/directChat")
                .setAllowedOrigins("*"); // 允许所有来源进行连接
    }

    // 【删除】不再需要 WebFlux 的 SimpleUrlHandlerMapping 和 WebSocketHandlerAdapter bean
}