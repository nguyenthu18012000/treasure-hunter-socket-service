package com.treasurehunter.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.event.dispatcher.SocketEventDispatcher;
import io.micrometer.common.lang.NonNullApi;
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
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        SocketRequestDTO request = objectMapper.readValue(
                message.getPayload(),
                SocketRequestDTO.class
        );

        dispatcher.dispatch(session, request);
    }
}
