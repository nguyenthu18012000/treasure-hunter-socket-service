package com.treasurehunter.socket.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunter.socket.model.movement.PlayerMovementInput;
import com.treasurehunter.socket.model.movement.PlayerMovementState;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlayerStateRegistry {

    private static final double PLAYER_SPEED = 160.0;
    private static final double WORLD_WIDTH = 6000.0;
    private static final double WORLD_HEIGHT = 6000.0;
    private static final double SPAWN_X = WORLD_WIDTH / 2.0;
    private static final double SPAWN_Y = WORLD_HEIGHT / 2.0;
    // Upper bound for how long the last velocity is applied between two inputs.
    // Clients send at least every 50ms while moving, so this only kicks in on lag spikes or a frozen tab.
    private static final long MAX_SERVER_DELTA_MS = 200L;

    private final Map<String, PlayerMovementState> playerStates = new ConcurrentHashMap<>();
    private final Map<String, Long> playerUpdateTimes = new ConcurrentHashMap<>();
    // sessionId -> playerId, set by the first movement message of the session
    private final Map<String, String> sessionPlayers = new ConcurrentHashMap<>();
    // playerId -> id of the session that currently owns that player
    private final Map<String, String> playerSessionIds = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PlayerStateRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public Collection<WebSocketSession> getOpenSessions() {
        return sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .toList();
    }

    public List<PlayerMovementState> getStatesExcept(String playerId) {
        List<PlayerMovementState> states = new ArrayList<>();
        playerStates.forEach((id, state) -> {
            if (!id.equals(playerId)) {
                states.add(state);
            }
        });
        return states;
    }

    public Optional<PlayerMovementInput> readMovementInput(Object payload) {
        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.convertValue(payload, PlayerMovementInput.class));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public Optional<String> getPlayerId(PlayerMovementInput input) {
        String playerId = input.getPlayerId();
        if (playerId == null || playerId.isBlank()) {
            playerId = input.getId();
        }

        return playerId == null || playerId.isBlank() ? Optional.empty() : Optional.of(playerId);
    }

    /**
     * Binds a session to a player on its first movement message.
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

    /** true khi người chơi đã có trong world (ví dụ tải lại trang: session mới nhưng state vẫn còn). */
    public boolean hasState(String playerId) {
        return playerStates.containsKey(playerId);
    }

    /**
     * @param spawnPosition vị trí xuất hiện, chỉ dùng khi người chơi chưa có trong world.
     *                      null thì dùng điểm xuất hiện mặc định (giữa map).
     */
    public Optional<PlayerMovementState> applyMovement(
            WebSocketSession session,
            String playerId,
            PlayerMovementInput input,
            PlayerPosition spawnPosition
    ) {
        if (!session.getId().equals(playerSessionIds.get(playerId))) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        PlayerMovementState currentState = playerStates.get(playerId);
        PlayerMovementState nextState = currentState == null
                ? createInitialState(playerId, input, spawnPosition, now)
                : copyState(currentState);

        // First move the player with the velocity that was active since the previous input,
        // then switch to the new direction. Applying the new direction to past time causes drift.
        if (currentState != null) {
            long previousUpdateTime = playerUpdateTimes.getOrDefault(playerId, now);
            double deltaSeconds = Math.min(Math.max(now - previousUpdateTime, 0L), MAX_SERVER_DELTA_MS) / 1000.0;
            nextState.setX(clamp(nextState.getX() + nextState.getVx() * deltaSeconds, 0.0, WORLD_WIDTH));
            nextState.setY(clamp(nextState.getY() + nextState.getVy() * deltaSeconds, 0.0, WORLD_HEIGHT));
        }

        double directionX = finiteOrZero(input.getDirectionX());
        double directionY = finiteOrZero(input.getDirectionY());
        double length = Math.hypot(directionX, directionY);
        if (length > 1.0) {
            directionX /= length;
            directionY /= length;
        }

        nextState.setVx(directionX * PLAYER_SPEED);
        nextState.setVy(directionY * PLAYER_SPEED);
        nextState.setDirectionX(directionX);
        nextState.setDirectionY(directionY);
        nextState.setSequence(input.getSequence() == null ? nextState.getSequence() : input.getSequence());
        nextState.setServerTime(now);
        if (input.getName() != null && !input.getName().isBlank()) {
            nextState.setName(input.getName());
        }
        nextState.setAnimation(resolveAnimation(nextState.getAnimation(), directionX, directionY));

        playerStates.put(playerId, nextState);
        playerUpdateTimes.put(playerId, now);
        return Optional.of(nextState);
    }

    /**
     * Removes a closed session. Returns the playerId only when this session still owned the player,
     * so a replaced session (page reload) does not remove the player from the world.
     */
    public Optional<PlayerPosition> removeBySession(WebSocketSession session) {
        sessions.remove(session.getId());

        String playerId = sessionPlayers.remove(session.getId());
        if (playerId == null || !playerSessionIds.remove(playerId, session.getId())) {
            return Optional.empty();
        }

        PlayerMovementState lastState = playerStates.remove(playerId);
        playerUpdateTimes.remove(playerId);
        long now = System.currentTimeMillis();
        return Optional.of(lastState == null
                ? new PlayerPosition(playerId, null, null, now)
                : positionAt(lastState, now));
    }

    /** Vị trí hiện tại của mọi người chơi đang online, dùng để lưu định kỳ. */
    public List<PlayerPosition> getCurrentPositions() {
        long now = System.currentTimeMillis();
        List<PlayerPosition> positions = new ArrayList<>();
        playerStates.values().forEach(state -> positions.add(positionAt(state, now)));
        return positions;
    }

    /**
     * x/y trong state chỉ được cập nhật khi có input, nên người chơi đang đi thì x/y đã cũ.
     * Tính vị trí ở thời điểm now giống cách applyMovement làm: đi tiếp theo vận tốc cuối,
     * tối đa MAX_SERVER_DELTA_MS. Dùng serverTime của chính state để x/y và thời gian luôn khớp nhau.
     */
    private PlayerPosition positionAt(PlayerMovementState state, long now) {
        double deltaSeconds = Math.min(Math.max(now - state.getServerTime(), 0L), MAX_SERVER_DELTA_MS) / 1000.0;
        double x = clamp(state.getX() + state.getVx() * deltaSeconds, 0.0, WORLD_WIDTH);
        double y = clamp(state.getY() + state.getVy() * deltaSeconds, 0.0, WORLD_HEIGHT);
        return new PlayerPosition(state.getPlayerId(), x, y, now);
    }

    private PlayerMovementState createInitialState(
            String playerId,
            PlayerMovementInput input,
            PlayerPosition spawnPosition,
            long now
    ) {
        PlayerMovementState state = new PlayerMovementState();
        state.setPlayerId(playerId);
        state.setId(playerId);
        state.setName(input.getName() == null || input.getName().isBlank() ? "Player " + playerId : input.getName());
        // Spawn position is decided by the server, never by the client.
        boolean hasSpawn = spawnPosition != null && spawnPosition.hasPosition()
                && Double.isFinite(spawnPosition.x()) && Double.isFinite(spawnPosition.y());
        state.setX(hasSpawn ? clamp(spawnPosition.x(), 0.0, WORLD_WIDTH) : SPAWN_X);
        state.setY(hasSpawn ? clamp(spawnPosition.y(), 0.0, WORLD_HEIGHT) : SPAWN_Y);
        state.setAnimation("idle-right");
        state.setSequence(0L);
        state.setServerTime(now);
        return state;
    }

    private PlayerMovementState copyState(PlayerMovementState state) {
        PlayerMovementState copy = new PlayerMovementState();
        copy.setPlayerId(state.getPlayerId());
        copy.setId(state.getId());
        copy.setName(state.getName());
        copy.setX(state.getX());
        copy.setY(state.getY());
        copy.setVx(state.getVx());
        copy.setVy(state.getVy());
        copy.setDirectionX(state.getDirectionX());
        copy.setDirectionY(state.getDirectionY());
        copy.setAnimation(state.getAnimation());
        copy.setSequence(state.getSequence());
        copy.setServerTime(state.getServerTime());
        return copy;
    }

    private String resolveAnimation(String previousAnimation, double directionX, double directionY) {
        boolean moving = directionX != 0.0 || directionY != 0.0;
        if (moving) {
            if (directionX < 0.0) return "walk-left";
            if (directionX > 0.0) return "walk-right";
            // Moving straight up/down keeps the last facing side.
            return previousAnimation != null && previousAnimation.contains("left") ? "walk-left" : "walk-right";
        }

        return previousAnimation != null && previousAnimation.contains("left") ? "idle-left" : "idle-right";
    }

    private double finiteOrZero(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
