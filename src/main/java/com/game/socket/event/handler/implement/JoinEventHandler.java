package com.game.socket.event.handler.implement;

import com.game.socket.constant.SocketEventConstant;
import com.game.socket.event.handler.SocketEventHandler;
import com.game.socket.event.handler.SocketPayloadReader;
import com.game.socket.model.room.MapEnterPayload;
import com.game.socket.model.room.PlayerJoinInput;
import com.game.socket.room.JoinResult;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerPosition;
import com.game.socket.state.PlayerSessionRegistry;
import com.game.socket.state.SpawnPositionResolver;
import com.game.socket.websocket.SocketMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;

/**
 * PLAYER_JOIN: gắn session với người chơi, đưa người chơi vào room theo vị trí đã lưu
 * (hoặc giữ nguyên room/vị trí nếu đang có trong room — tải lại trang), trả MAP_ENTER.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinEventHandler implements SocketEventHandler {
    private static final CloseStatus REPLACED_BY_NEW_CONNECTION =
            CloseStatus.POLICY_VIOLATION.withReason("Player connected from another session");

    private final SocketPayloadReader payloadReader;
    private final PlayerSessionRegistry sessionRegistry;
    private final RoomManager roomManager;
    private final SpawnPositionResolver spawnPositionResolver;
    private final SocketMessageSender messageSender;

    @Override
    public String getEvent() {
        return SocketEventConstant.PLAYER_JOIN;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        Optional<PlayerJoinInput> inputOptional = payloadReader.read(payload, PlayerJoinInput.class);
        String playerId = inputOptional.map(PlayerJoinInput::getPlayerId).orElse(null);
        if (playerId == null || playerId.isBlank()) {
            log.warn("Ignored PLAYER_JOIN without playerId from session {}", session.getId());
            return;
        }

        // Khoá theo playerId: không xen kẽ với việc đóng session cũ của cùng người chơi
        // (GameWebSocketHandler.afterConnectionClosed: bỏ bind + rời room trong cùng khoá).
        Object lock = sessionRegistry.lockFor(playerId);
        PlayerSessionRegistry.BindResult bind;
        boolean inRoom;
        synchronized (lock) {
            bind = sessionRegistry.bindSession(session, playerId);
            if (!bind.accepted()) {
                log.warn("Ignored PLAYER_JOIN for playerId {}: session {} belongs to another player", playerId, session.getId());
                return;
            }
            inRoom = roomManager.isInRoom(playerId);
        }
        if (bind.replacedSession() != null) {
            closeReplacedSession(bind.replacedSession());
        }

        // Lần đầu vào game: lấy vị trí đã lưu để xuất hiện đúng map, đúng chỗ lần trước.
        // Gọi Feign ngoài khoá (có thể chờ tới vài giây).
        PlayerPosition saved = inRoom ? null : spawnPositionResolver.resolve(playerId).orElse(null);

        JoinResult result;
        synchronized (lock) {
            // Trong lúc gọi Feign, session này có thể đã đóng hoặc bị session khác thay:
            // vào room lúc này sẽ để lại người chơi "ma" không có session.
            if (!playerId.equals(sessionRegistry.getPlayerId(session).orElse(null))) {
                log.debug("Skip PLAYER_JOIN of {}: session {} no longer owns the player", playerId, session.getId());
                return;
            }
            result = roomManager.join(playerId, inputOptional.get().getName(), saved, System.currentTimeMillis());
        }
        String mapId = result.room().id();
        log.debug("Player {} joined map {} (rejoined: {})", playerId, mapId, result.rejoined());

        messageSender.send(session, SocketEventConstant.MAP_ENTER, new MapEnterPayload(
                mapId,
                result.room().definition().width(),
                result.room().definition().height(),
                result.self(),
                result.room().snapshotExcept(playerId)
        ));
        messageSender.broadcastToRoom(mapId, playerId, SocketEventConstant.PLAYER_MOVE, result.self());
    }

    private void closeReplacedSession(WebSocketSession oldSession) {
        try {
            oldSession.close(REPLACED_BY_NEW_CONNECTION);
        } catch (IOException e) {
            log.warn("Failed to close replaced session {}", oldSession.getId(), e);
        }
    }
}
