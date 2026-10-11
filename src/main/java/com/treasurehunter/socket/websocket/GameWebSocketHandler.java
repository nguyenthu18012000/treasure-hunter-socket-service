package com.treasurehunter.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.event.dispatcher.SocketEventDispatcher;
import com.treasurehunter.socket.state.PlayerStateRegistry;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SocketEventDispatcher dispatcher;
    private final PlayerStateRegistry playerStateRegistry;

    public GameWebSocketHandler(
            SocketEventDispatcher dispatcher,
            PlayerStateRegistry playerStateRegistry
    ) {
        this.dispatcher = dispatcher;
        this.playerStateRegistry = playerStateRegistry;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        playerStateRegistry.addSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
        playerStateRegistry.removeBySession(session).ifPresent(this::broadcastPlayerLeave);
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws IOException {
        SocketRequestDTO request = objectMapper.readValue(
                message.getPayload(),
                SocketRequestDTO.class
        );

        dispatcher.dispatch(session, request);
    }

    private void broadcastPlayerLeave(String playerId) {
        SocketRequestDTO response = new SocketRequestDTO();
        response.setEvent(SocketEventConstant.PLAYER_LEAVE);
        response.setPayload(playerId);

        try {
            broadcastToAll(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException ignored) {
        }
    }

    private void broadcastToAll(TextMessage message) {
        for (WebSocketSession client : sessions) {
            if (!client.isOpen()) {
                continue;
            }

            try {
                synchronized (client) {
                    client.sendMessage(message);
                }
            } catch (IOException ignored) {
                sessions.remove(client);
            }
        }
    }
}
