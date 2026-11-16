package com.game.socket.state;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gắn WebSocket session với người chơi (playerId). Không giữ state di chuyển:
 * vị trí nằm trong {@link com.game.socket.room.MapRoom}.
 */
@Component
public class PlayerSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // sessionId -> playerId, set by PLAYER_JOIN
    private final Map<String, String> sessionPlayers = new ConcurrentHashMap<>();
    // playerId -> id of the session that currently owns that player
    private final Map<String, String> playerSessionIds = new ConcurrentHashMap<>();

    /**
     * Khoá theo playerId (chia sọc, số lượng cố định nên không phình bộ nhớ).
     * JOIN (bind + vào room) và đóng kết nối (bỏ bind + rời room) của cùng người chơi chạy từ hai
     * session khác nhau, tức hai luồng khác nhau: phải giữ khoá này để hai bước đó không xen kẽ
     * (ví dụ F5: session cũ đóng, session mới JOIN ngay lúc đó).
     * Chỉ giữ trong đoạn ngắn, không gọi mạng (Feign/Kafka) khi đang giữ.
     */
    private static final int LOCK_STRIPES = 64;
    private final Object[] playerLocks = new Object[LOCK_STRIPES];

    public PlayerSessionRegistry() {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            playerLocks[i] = new Object();
        }
    }

    public Object lockFor(String playerId) {
        return playerLocks[Math.floorMod(playerId.hashCode(), LOCK_STRIPES)];
    }

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    /** playerId đã bind với session (kể cả khi session đã bị session mới thay), null nếu chưa JOIN. */
    public Optional<String> boundPlayerId(WebSocketSession session) {
        return Optional.ofNullable(sessionPlayers.get(session.getId()));
    }

    /**
     * Binds a session to a player (PLAYER_JOIN).
     * A session keeps the same player for its whole life; a later message with another id is rejected.
     * If another session already owns this player (e.g. page reload), the new session takes over
     * and the old one is returned so the caller can close it.
     */
    public BindResult bindSession(WebSocketSession session, String playerId) {
        String boundPlayerId = sessionPlayers.putIfAbsent(session.getId(), playerId);
        if (boundPlayerId != null) {
            return boundPlayerId.equals(playerId) ? BindResult.ok() : BindResult.rejected();
        }

        String previousSessionId = playerSessionIds.put(playerId, session.getId());
        if (previousSessionId == null || previousSessionId.equals(session.getId())) {
            return BindResult.ok();
        }

        sessionPlayers.remove(previousSessionId);
        return BindResult.replaced(sessions.remove(previousSessionId));
    }

    /** playerId của session, chỉ khi session vẫn đang sở hữu người chơi đó (chưa bị session mới thay). */
    public Optional<String> getPlayerId(WebSocketSession session) {
        String playerId = sessionPlayers.get(session.getId());
        if (playerId == null || !session.getId().equals(playerSessionIds.get(playerId))) {
            return Optional.empty();
        }
        return Optional.of(playerId);
    }

    /** Session đang mở của người chơi. */
    public Optional<WebSocketSession> getSession(String playerId) {
        String sessionId = playerId == null ? null : playerSessionIds.get(playerId);
        WebSocketSession session = sessionId == null ? null : sessions.get(sessionId);
        return session != null && session.isOpen() ? Optional.of(session) : Optional.empty();
    }

    /**
     * Removes a closed session. Returns the playerId only when this session still owned the player,
     * so a replaced session (page reload) does not remove the player from the world.
     */
    public Optional<String> removeBySession(WebSocketSession session) {
        sessions.remove(session.getId());

        String playerId = sessionPlayers.remove(session.getId());
        if (playerId == null || !playerSessionIds.remove(playerId, session.getId())) {
            return Optional.empty();
        }
        return Optional.of(playerId);
    }

    public record BindResult(boolean accepted, WebSocketSession replacedSession) {
        static BindResult ok() {
            return new BindResult(true, null);
        }

        static BindResult rejected() {
            return new BindResult(false, null);
        }

        static BindResult replaced(WebSocketSession replacedSession) {
            return new BindResult(true, replacedSession);
        }
    }
}
