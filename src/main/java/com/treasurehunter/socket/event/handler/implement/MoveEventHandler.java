package com.treasurehunter.socket.event.handler.implement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.state.PlayerStateRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Component
public class MoveEventHandler implements SocketEventHandler {
    private final ObjectMapper objectMapper;
    private final PlayerStateRegistry playerStateRegistry;

    public MoveEventHandler(
            ObjectMapper objectMapper,
            PlayerStateRegistry playerStateRegistry
    ) {
        this.objectMapper = objectMapper;
        this.playerStateRegistry = playerStateRegistry;
    }

    @Override
    public String getEvent() {
        return SocketEventConstant.MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        log.info("Handling MOVE event with payload: {}", payload);

        playerStateRegistry.getPlayerId(payload).ifPresent(playerId -> {
            if (!playerStateRegistry.isSessionRegisteredForPlayer(session, playerId)) {
                sendExistingPlayersToNewSession(session, playerId);
            }
            playerStateRegistry.update(session, playerId, payload);
        });
    }

    private void sendExistingPlayersToNewSession(WebSocketSession session, String playerId) {
        if (!session.isOpen()) {
            return;
        }

        for (Object existingState : playerStateRegistry.getStatesExcept(playerId)) {
            try {
                SocketRequestDTO response = new SocketRequestDTO();
                response.setEvent(SocketEventConstant.MOVE);
                response.setPayload(existingState);

                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            } catch (IOException e) {
                log.error("Failed to send existing player state to playerId: {}", playerId, e);
                return;
            }
        }
    }
}
