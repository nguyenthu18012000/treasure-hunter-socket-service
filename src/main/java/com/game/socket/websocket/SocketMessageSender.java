package com.game.socket.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.socket.model.request.SocketRequestDTO;
import com.game.socket.room.MapRoom;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class SocketMessageSender {
    private final ObjectMapper objectMapper;
    private final PlayerSessionRegistry sessionRegistry;
    private final RoomManager roomManager;

    public SocketMessageSender(
            ObjectMapper objectMapper,
            PlayerSessionRegistry sessionRegistry,
            RoomManager roomManager
    ) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.roomManager = roomManager;
    }

    public void send(WebSocketSession session, String event, Object payload) {
        encode(event, payload).ifPresent(message -> sendRaw(session, message));
    }

    /** Gửi cho mọi người chơi đang ở room mapId. */
    public void broadcastToRoom(String mapId, String event, Object payload) {
        broadcastToRoom(mapId, null, event, payload);
    }

    /** Gửi cho mọi người chơi đang ở room mapId, trừ exceptPlayerId (null = không trừ ai). */
    public void broadcastToRoom(String mapId, String exceptPlayerId, String event, Object payload) {
        Optional<MapRoom> room = roomManager.room(mapId);
        if (room.isEmpty()) {
            return;
        }

        encode(event, payload).ifPresent(message -> {
            for (String playerId : room.get().playerIds()) {
                if (playerId.equals(exceptPlayerId)) {
                    continue;
                }
                sessionRegistry.getSession(playerId).ifPresent(session -> sendRaw(session, message));
            }
        });
    }

    private Optional<TextMessage> encode(String event, Object payload) {
        SocketRequestDTO response = new SocketRequestDTO();
        response.setEvent(event);
        response.setPayload(payload);
        try {
            return Optional.of(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JsonProcessingException e) {
            log.error("Failed to encode {} message", event, e);
            return Optional.empty();
        }
    }

    private void sendRaw(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return;
        }

        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send message to session {}", session.getId(), e);
        }
    }
}
