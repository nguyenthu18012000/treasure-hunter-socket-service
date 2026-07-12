package com.treasurehunter.socket.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> playerSessions = new ConcurrentHashMap<>();

    public void add(String playerId, WebSocketSession session) {
        playerSessions.put(playerId, session);
    }

    public WebSocketSession get(String playerId) {
        return playerSessions.get(playerId);
    }

    public void remove(String playerId) {
        playerSessions.remove(playerId);
    }

    public Collection<WebSocketSession> getAll() {
        return playerSessions.values();
    }
}
