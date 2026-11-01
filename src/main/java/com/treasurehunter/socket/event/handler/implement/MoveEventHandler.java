package com.treasurehunter.socket.event.handler.implement;

import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import com.treasurehunter.socket.model.movement.PlayerMovementInput;
import com.treasurehunter.socket.state.PlayerStateRegistry;
import com.treasurehunter.socket.websocket.SocketMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class MoveEventHandler implements SocketEventHandler {
    private static final CloseStatus REPLACED_BY_NEW_CONNECTION =
            CloseStatus.POLICY_VIOLATION.withReason("Player connected from another session");

    private final PlayerStateRegistry playerStateRegistry;
    private final SocketMessageSender messageSender;

    public MoveEventHandler(PlayerStateRegistry playerStateRegistry, SocketMessageSender messageSender) {
        this.playerStateRegistry = playerStateRegistry;
        this.messageSender = messageSender;
    }

    @Override
    public String getEvent() {
        return SocketEventConstant.PLAYER_MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        log.debug("Handling PLAYER_MOVE from session {}: {}", session.getId(), payload);

        Optional<PlayerMovementInput> inputOptional = playerStateRegistry.readMovementInput(payload);
        if (inputOptional.isEmpty()) {
            return;
        }

        PlayerMovementInput input = inputOptional.get();
        Optional<String> playerIdOptional = playerStateRegistry.getPlayerId(input);
        if (playerIdOptional.isEmpty()) {
            log.warn("Ignored PLAYER_MOVE without playerId from session {}", session.getId());
            return;
        }

        String playerId = playerIdOptional.get();
        PlayerStateRegistry.BindResult bind = playerStateRegistry.bindSession(session, playerId);
        if (!bind.accepted()) {
            log.warn("Ignored PLAYER_MOVE for playerId {}: session {} belongs to another player", playerId, session.getId());
            return;
        }
        if (bind.replacedSession() != null) {
            closeReplacedSession(bind.replacedSession());
        }

        playerStateRegistry.applyMovement(session, playerId, input)
                .ifPresent(state -> messageSender.broadcast(SocketEventConstant.PLAYER_MOVE, state));
    }

    private void closeReplacedSession(WebSocketSession oldSession) {
        try {
            oldSession.close(REPLACED_BY_NEW_CONNECTION);
        } catch (IOException e) {
            log.warn("Failed to close replaced session {}", oldSession.getId(), e);
        }
    }
}
