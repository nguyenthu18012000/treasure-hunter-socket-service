package com.treasurehunter.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.dispatcher.SocketEventDispatcher;
import com.treasurehunter.socket.kafka.CharacterPositionPublisher;
import com.treasurehunter.socket.model.movement.PlayerMovementState;
import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.state.PlayerStateRegistry;
import com.treasurehunter.socket.state.SpawnPositionResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SocketEventDispatcher dispatcher;
    private final PlayerStateRegistry playerStateRegistry;
    private final SocketMessageSender messageSender;
    private final CharacterPositionPublisher positionPublisher;
    private final SpawnPositionResolver spawnPositionResolver;

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            SocketEventDispatcher dispatcher,
            PlayerStateRegistry playerStateRegistry,
            SocketMessageSender messageSender,
            CharacterPositionPublisher positionPublisher,
            SpawnPositionResolver spawnPositionResolver
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.playerStateRegistry = playerStateRegistry;
        this.messageSender = messageSender;
        this.positionPublisher = positionPublisher;
        this.spawnPositionResolver = spawnPositionResolver;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        playerStateRegistry.addSession(session);

        // Show the new connection everyone who is already in the world.
        // Its own player is not known yet (it arrives with the first PLAYER_MOVE).
        for (PlayerMovementState state : playerStateRegistry.getStatesExcept("")) {
            messageSender.send(session, SocketEventConstant.PLAYER_MOVE, state);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        playerStateRegistry.removeBySession(session).ifPresent(lastPosition -> {
            positionPublisher.publishOnDisconnect(lastPosition);
            spawnPositionResolver.rememberLeave(lastPosition);
            messageSender.broadcast(SocketEventConstant.PLAYER_LEAVE, lastPosition.playerId());
        });
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        // A bad message from one client must not throw: Spring would close the connection.
        try {
            SocketRequestDTO request = objectMapper.readValue(message.getPayload(), SocketRequestDTO.class);
            if (request == null || request.getEvent() == null) {
                log.warn("Ignored socket message without event from session {}", session.getId());
                return;
            }

            dispatcher.dispatch(session, request);
        } catch (Exception e) {
            log.warn("Ignored invalid socket message from session {}: {}", session.getId(), e.getMessage());
        }
    }
}
