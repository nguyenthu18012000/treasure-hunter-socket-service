package com.treasurehunter.socket.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.state.PlayerStateRegistry;
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
    private final PlayerStateRegistry playerStateRegistry;

    public SocketMessageSender(ObjectMapper objectMapper, PlayerStateRegistry playerStateRegistry) {
        this.objectMapper = objectMapper;
        this.playerStateRegistry = playerStateRegistry;
    }

    public void send(WebSocketSession session, String event, Object payload) {
        encode(event, payload).ifPresent(message -> sendRaw(session, message));
    }

    public void broadcast(String event, Object payload) {
        encode(event, payload).ifPresent(message -> {
            for (WebSocketSession client : playerStateRegistry.getOpenSessions()) {
                sendRaw(client, message);
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
