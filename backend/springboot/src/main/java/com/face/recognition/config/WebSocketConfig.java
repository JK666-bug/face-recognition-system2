package com.face.recognition.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 浏览器带 Origin；原先只允许 http://localhost:5173，用 127.0.0.1 / 局域网 IP 访问 Vite 会导致握手被拒
        // 生产环境请改为具体域名白名单，勿用 *
        registry.addHandler(alertWebSocketHandler, "/ws-alerts")
                .setAllowedOriginPatterns("*");
    }
}
