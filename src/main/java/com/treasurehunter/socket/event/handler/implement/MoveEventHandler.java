package com.treasurehunter.socket.event.handler.implement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import com.treasurehunter.socket.model.movement.PlayerMovementState;
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
        return SocketEventConstant.PLAYER_MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        log.info("Handling PLAYER_MOVE event with payload: {}", payload);

        playerStateRegistry.getPlayerId(payload).ifPresent(playerId -> {
            if (!playerStateRegistry.isSessionRegisteredForPlayer(session, playerId)) {
                sendExistingPlayersToNewSession(session, playerId);
            }
            playerStateRegistry.applyMovement(session, payload).ifPresent(this::broadcastMovementState);
        });
    }

    private void sendExistingPlayersToNewSession(WebSocketSession session, String playerId) {
        if (!session.isOpen()) {
            return;
        }

        for (PlayerMovementState existingState : playerStateRegistry.getStatesExcept(playerId)) {
            try {
                SocketRequestDTO response = new SocketRequestDTO();
                response.setEvent(SocketEventConstant.PLAYER_MOVE);
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

    private void broadcastMovementState(PlayerMovementState state) {
        SocketRequestDTO response = new SocketRequestDTO();
        response.setEvent(SocketEventConstant.PLAYER_MOVE);
        response.setPayload(state);

        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(response));
            for (WebSocketSession client : playerStateRegistry.getOpenSessions()) {
                try {
                    synchronized (client) {
                        client.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.error("Failed to broadcast movement state for playerId: {}", state.getPlayerId(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to encode movement state for playerId: {}", state.getPlayerId(), e);
        }
    }
}
