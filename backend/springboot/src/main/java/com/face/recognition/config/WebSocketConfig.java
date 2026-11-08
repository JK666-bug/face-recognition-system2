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
        // Browsers send Origin. Allowing only http://localhost:5173 rejects 127.0.0.1 and LAN hosts.
        // In production, list real origins instead of *.
        registry.addHandler(alertWebSocketHandler, "/ws-alerts")
                .setAllowedOriginPatterns("*");
    }
}
