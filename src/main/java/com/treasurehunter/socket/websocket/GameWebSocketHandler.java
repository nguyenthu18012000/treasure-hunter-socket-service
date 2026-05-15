package com.treasurehunter.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.event.dispatcher.SocketEventDispatcher;
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

    public GameWebSocketHandler(SocketEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws IOException {
        SocketRequestDTO request = objectMapper.readValue(
                message.getPayload(),
                SocketRequestDTO.class
        );

        dispatcher.dispatch(session, request);
        broadcastToOthers(session, message);
    }

    private void broadcastToOthers(WebSocketSession sender, TextMessage message) {
        for (WebSocketSession client : sessions) {
            if (client == sender || !client.isOpen()) {
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
