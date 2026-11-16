package com.game.socket.event.handler.implement;

import com.game.socket.constant.SocketEventConstant;
import com.game.socket.event.handler.SocketEventHandler;
import com.game.socket.event.handler.SocketPayloadReader;
import com.game.socket.model.movement.PlayerMovementInput;
import com.game.socket.room.MapRoom;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerSessionRegistry;
import com.game.socket.websocket.SocketMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

/**
 * PLAYER_MOVE: áp input di chuyển rồi broadcast state trong room.
 * Bỏ qua khi session chưa PLAYER_JOIN, playerId không phải của session, hoặc mapId khác room hiện tại
 * (input cũ còn trên đường truyền sau khi đổi map không được đẩy người chơi lệch khỏi spawn mới).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MoveEventHandler implements SocketEventHandler {

    private final SocketPayloadReader payloadReader;
    private final PlayerSessionRegistry sessionRegistry;
    private final RoomManager roomManager;
    private final SocketMessageSender messageSender;

    @Override
    public String getEvent() {
        return SocketEventConstant.PLAYER_MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        Optional<PlayerMovementInput> inputOptional = payloadReader.read(payload, PlayerMovementInput.class);
        if (inputOptional.isEmpty()) {
            return;
        }
        PlayerMovementInput input = inputOptional.get();

        Optional<String> playerIdOptional = sessionRegistry.getPlayerId(session);
        if (playerIdOptional.isEmpty()) {
            log.debug("Ignored PLAYER_MOVE from session {}: not joined", session.getId());
            return;
        }
        String playerId = playerIdOptional.get();

        String inputPlayerId = input.getPlayerId() == null || input.getPlayerId().isBlank()
                ? input.getId()
                : input.getPlayerId();
        if (inputPlayerId != null && !inputPlayerId.isBlank() && !inputPlayerId.equals(playerId)) {
            log.warn("Ignored PLAYER_MOVE for playerId {}: session {} belongs to {}", inputPlayerId, session.getId(), playerId);
            return;
        }

        Optional<MapRoom> room = roomManager.roomOf(playerId);
        if (room.isEmpty() || !room.get().id().equals(input.getMapId())) {
            log.debug("Ignored PLAYER_MOVE of {} for map {}: player is in {}",
                    playerId, input.getMapId(), room.map(MapRoom::id).orElse(null));
            return;
        }

        room.get().applyMovement(playerId, input, System.currentTimeMillis())
                .ifPresent(state -> messageSender.broadcastToRoom(room.get().id(), SocketEventConstant.PLAYER_MOVE, state));
    }
}
