package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementInput;
import com.game.socket.model.movement.PlayerMovementState;
import com.game.socket.state.PlayerPosition;
import com.game.socket.world.MapDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Một room = một map (roomId = mapId). Giữ người chơi đang ở map này và mô phỏng di chuyển của họ,
 * clamp theo kích thước của chính map đó.
 *
 * Sau này: thêm danh sách quái (monsters) và tick(now) chạy theo từng room tại đây,
 * vì quái, va chạm, chiến đấu chỉ liên quan người chơi cùng map. Task này chưa làm.
 *
 * Thread-safety: tin nhắn của một session được Spring xử lý tuần tự nên một người chơi không bị
 * cập nhật song song; các luồng khác (publisher định kỳ, broadcast) chỉ đọc qua ConcurrentHashMap.
 * State lưu trong map không bị sửa tại chỗ: mỗi lần cập nhật thay bằng bản sao mới.
 */
public class MapRoom {

    static final double PLAYER_SPEED = 160.0;
    // Upper bound for how long the last velocity is applied between two inputs.
    // Clients send at least every 50ms while moving, so this only kicks in on lag spikes or a frozen tab.
    static final long MAX_SERVER_DELTA_MS = 200L;

    private final MapDefinition definition;
    private final Map<String, PlayerMovementState> players = new ConcurrentHashMap<>();
    private final Map<String, Long> playerUpdateTimes = new ConcurrentHashMap<>();

    public MapRoom(MapDefinition definition) {
        this.definition = definition;
    }

    public String id() {
        return definition.id();
    }

    public MapDefinition definition() {
        return definition;
    }

    /** Đưa người chơi vào room tại (x, y), đứng yên, sequence 0. Vị trí do server quyết định. */
    public PlayerMovementState enter(String playerId, String name, double x, double y, long now) {
        PlayerMovementState state = new PlayerMovementState();
        state.setPlayerId(playerId);
        state.setId(playerId);
        state.setName(name == null || name.isBlank() ? "Player " + playerId : name);
        state.setMapId(definition.id());
        state.setX(clamp(x, 0.0, definition.width()));
        state.setY(clamp(y, 0.0, definition.height()));
        state.setAnimation("idle-right");
        state.setSequence(0L);
        state.setServerTime(now);

        players.put(playerId, state);
        playerUpdateTimes.put(playerId, now);
        return copyState(state);
    }

    /**
     * Người chơi đã có trong room vào lại (tải lại trang: session mới, client đếm sequence lại từ 0).
     * Chốt vị trí hiện tại, dừng di chuyển, reset sequence.
     */
    public Optional<PlayerMovementState> rejoin(String playerId, String name, long now) {
        PlayerMovementState current = players.get(playerId);
        if (current == null) {
            return Optional.empty();
        }

        PlayerMovementState next = copyState(current);
        PlayerPosition position = positionAt(current, now);
        next.setX(position.x());
        next.setY(position.y());
        next.setVx(0.0);
        next.setVy(0.0);
        next.setDirectionX(0.0);
        next.setDirectionY(0.0);
        next.setAnimation(resolveAnimation(current.getAnimation(), 0.0, 0.0));
        next.setSequence(0L);
        next.setServerTime(now);
        if (name != null && !name.isBlank()) {
            next.setName(name);
        }

        players.put(playerId, next);
        playerUpdateTimes.put(playerId, now);
        return Optional.of(copyState(next));
    }

    /** Rời room. Trả về vị trí lúc rời (tính tới now). */
    public Optional<PlayerPosition> leave(String playerId, long now) {
        PlayerMovementState lastState = players.remove(playerId);
        playerUpdateTimes.remove(playerId);
        return lastState == null ? Optional.empty() : Optional.of(positionAt(lastState, now));
    }

    public boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    public Optional<PlayerMovementState> getState(String playerId) {
        PlayerMovementState state = players.get(playerId);
        return state == null ? Optional.empty() : Optional.of(copyState(state));
    }

    public Optional<PlayerMovementState> applyMovement(String playerId, PlayerMovementInput input, long now) {
        PlayerMovementState currentState = players.get(playerId);
        if (currentState == null) {
            return Optional.empty();
        }
        PlayerMovementState nextState = copyState(currentState);

        // First move the player with the velocity that was active since the previous input,
        // then switch to the new direction. Applying the new direction to past time causes drift.
        long previousUpdateTime = playerUpdateTimes.getOrDefault(playerId, now);
        double deltaSeconds = Math.min(Math.max(now - previousUpdateTime, 0L), MAX_SERVER_DELTA_MS) / 1000.0;
        nextState.setX(clamp(nextState.getX() + nextState.getVx() * deltaSeconds, 0.0, definition.width()));
        nextState.setY(clamp(nextState.getY() + nextState.getVy() * deltaSeconds, 0.0, definition.height()));

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

        players.put(playerId, nextState);
        playerUpdateTimes.put(playerId, now);
        return Optional.of(copyState(nextState));
    }

    public Optional<PlayerPosition> positionAt(String playerId, long now) {
        PlayerMovementState state = players.get(playerId);
        return state == null ? Optional.empty() : Optional.of(positionAt(state, now));
    }

    /** State của mọi người trong room trừ playerId (gửi kèm MAP_ENTER). */
    public List<PlayerMovementState> snapshotExcept(String playerId) {
        List<PlayerMovementState> states = new ArrayList<>();
        players.forEach((id, state) -> {
            if (!id.equals(playerId)) {
                states.add(copyState(state));
            }
        });
        return states;
    }

    public Set<String> playerIds() {
        return Set.copyOf(players.keySet());
    }

    /** Vị trí hiện tại của mọi người chơi trong room, dùng để lưu định kỳ. */
    public List<PlayerPosition> currentPositions(long now) {
        List<PlayerPosition> positions = new ArrayList<>();
        players.values().forEach(state -> positions.add(positionAt(state, now)));
        return positions;
    }

    /**
     * x/y trong state chỉ được cập nhật khi có input, nên người chơi đang đi thì x/y đã cũ.
     * Tính vị trí ở thời điểm now giống cách applyMovement làm: đi tiếp theo vận tốc cuối,
     * tối đa MAX_SERVER_DELTA_MS. Dùng serverTime của chính state để x/y và thời gian luôn khớp nhau.
     */
    private PlayerPosition positionAt(PlayerMovementState state, long now) {
        double deltaSeconds = Math.min(Math.max(now - state.getServerTime(), 0L), MAX_SERVER_DELTA_MS) / 1000.0;
        double x = clamp(state.getX() + state.getVx() * deltaSeconds, 0.0, definition.width());
        double y = clamp(state.getY() + state.getVy() * deltaSeconds, 0.0, definition.height());
        return new PlayerPosition(state.getPlayerId(), definition.id(), x, y, now);
    }

    private static PlayerMovementState copyState(PlayerMovementState state) {
        PlayerMovementState copy = new PlayerMovementState();
        copy.setPlayerId(state.getPlayerId());
        copy.setId(state.getId());
        copy.setName(state.getName());
        copy.setMapId(state.getMapId());
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

    private static String resolveAnimation(String previousAnimation, double directionX, double directionY) {
        boolean moving = directionX != 0.0 || directionY != 0.0;
        if (moving) {
            if (directionX < 0.0) return "walk-left";
            if (directionX > 0.0) return "walk-right";
            // Moving straight up/down keeps the last facing side.
            return previousAnimation != null && previousAnimation.contains("left") ? "walk-left" : "walk-right";
        }

        return previousAnimation != null && previousAnimation.contains("left") ? "idle-left" : "idle-right";
    }

    private static double finiteOrZero(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? 0.0 : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
