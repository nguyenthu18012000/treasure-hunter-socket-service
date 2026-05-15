package com.treasurehunter.socket.event.handler.implement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import com.treasurehunter.socket.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Component
public class MoveEventHandler implements SocketEventHandler {
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public MoveEventHandler(
            WebSocketSessionManager sessionManager,
            ObjectMapper objectMapper
    ) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getEvent() {
        return SocketEventConstant.MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        log.info("Handling MOVE event with payload: {}", payload);

        String message;
        try {
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }

        for (WebSocketSession s : sessionManager.getAll()) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Send failed", e);
                }
            }
        }
    }
}
