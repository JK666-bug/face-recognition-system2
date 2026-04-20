package com.face.recognition.config;

import com.face.recognition.service.AlertWebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private AlertWebSocketService alertWebSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        alertWebSocketService.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        alertWebSocketService.unregister(session);
    }
}
