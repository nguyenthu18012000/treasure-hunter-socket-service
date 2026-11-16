package com.game.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.socket.constant.SocketEventConstant;
import com.game.socket.event.dispatcher.SocketEventDispatcher;
import com.game.socket.kafka.CharacterPositionPublisher;
import com.game.socket.model.request.SocketRequestDTO;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerPosition;
import com.game.socket.state.PlayerSessionRegistry;
import com.game.socket.state.SpawnPositionResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SocketEventDispatcher dispatcher;
    private final PlayerSessionRegistry sessionRegistry;
    private final RoomManager roomManager;
    private final SocketMessageSender messageSender;
    private final CharacterPositionPublisher positionPublisher;
    private final SpawnPositionResolver spawnPositionResolver;

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            SocketEventDispatcher dispatcher,
            PlayerSessionRegistry sessionRegistry,
            RoomManager roomManager,
            SocketMessageSender messageSender,
            CharacterPositionPublisher positionPublisher,
            SpawnPositionResolver spawnPositionResolver
    ) {
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.sessionRegistry = sessionRegistry;
        this.roomManager = roomManager;
        this.messageSender = messageSender;
        this.positionPublisher = positionPublisher;
        this.spawnPositionResolver = spawnPositionResolver;
    }

    /** Chưa biết người chơi là ai: client gửi PLAYER_JOIN, server trả MAP_ENTER kèm người cùng map. */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessionRegistry.addSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Optional<String> boundPlayerId = sessionRegistry.boundPlayerId(session);
        if (boundPlayerId.isEmpty()) {
            sessionRegistry.removeBySession(session);
            return;
        }
        String playerId = boundPlayerId.get();

        // Bỏ bind + rời room là một bước nguyên tử với JOIN của session khác (cùng khoá theo playerId).
        // Nếu không: F5 → session cũ đã bỏ bind, session mới JOIN thấy người chơi còn trong room nên rejoin
        // và nhận MAP_ENTER, rồi luồng của session cũ mới gọi leave → server mất người chơi.
        long now = System.currentTimeMillis();
        Optional<PlayerPosition> lastPosition;
        synchronized (sessionRegistry.lockFor(playerId)) {
            // Session đã bị session mới thay (tải lại trang) thì không còn sở hữu người chơi: không làm gì.
            if (sessionRegistry.removeBySession(session).isEmpty()) {
                return;
            }
            lastPosition = roomManager.leave(playerId, now);
        }

        // Ngoài khoá: Kafka có thể chờ tới max.block.ms.
        // Thứ tự publish → rememberLeave giữ như cũ: rememberLeave xoá cờ canSave=false mà publish cần đọc.
        if (lastPosition.isEmpty()) {
            // Đã bind nhưng chưa vào room: chỉ dọn trạng thái của SpawnPositionResolver.
            spawnPositionResolver.rememberLeave(new PlayerPosition(playerId, null, null, null, now));
            return;
        }
        PlayerPosition position = lastPosition.get();
        positionPublisher.publishOnDisconnect(position);
        spawnPositionResolver.rememberLeave(position);
        messageSender.broadcastToRoom(position.mapId(), SocketEventConstant.PLAYER_LEAVE, playerId);
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
