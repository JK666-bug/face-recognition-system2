package com.face.recognition.service;

import com.face.recognition.dto.AlertEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AlertWebSocketService {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcast(AlertEventDTO event) {
        if (sessions.isEmpty()) {
            log.warn("Suspect alert WebSocket broadcast skipped: no connected clients (keep Dashboard open to receive live alerts)");
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            Iterator<WebSocketSession> it = sessions.iterator();
            while (it.hasNext()) {
                WebSocketSession session = it.next();
                if (!session.isOpen()) {
                    it.remove();
                    continue;
                }
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("WebSocket send failed, removing session: {}", e.getMessage());
                    it.remove();
                }
            }
        } catch (Exception e) {
            log.warn("Failed broadcasting alert event: {}", e.getMessage());
        }
    }
}
