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
    private static final long MAX_SERVER_DELTA_MS = 100L;

    private final Map<String, PlayerMovementState> playerStates = new ConcurrentHashMap<>();
    private final Map<String, Long> playerUpdateTimes = new ConcurrentHashMap<>();
    private final Map<String, String> sessionPlayers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PlayerStateRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> getPlayerId(Object payload) {
        return readMovementInput(payload).flatMap(this::getPlayerId);
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

    public boolean isSessionRegisteredForPlayer(WebSocketSession session, String playerId) {
        return playerId.equals(sessionPlayers.get(session.getId()));
    }

    public Optional<PlayerMovementState> applyMovement(WebSocketSession session, Object payload) {
        Optional<PlayerMovementInput> inputOptional = readMovementInput(payload);
        if (inputOptional.isEmpty()) {
            return Optional.empty();
        }

        PlayerMovementInput input = inputOptional.get();
        Optional<String> playerIdOptional = getPlayerId(input);
        if (playerIdOptional.isEmpty()) {
            return Optional.empty();
        }

        String playerId = playerIdOptional.get();
        String previousPlayerId = sessionPlayers.put(session.getId(), playerId);
        if (previousPlayerId != null && !previousPlayerId.equals(playerId)) {
            playerStates.remove(previousPlayerId);
            playerUpdateTimes.remove(previousPlayerId);
        }

        long now = System.currentTimeMillis();
        PlayerMovementState currentState = playerStates.get(playerId);
        long previousUpdateTime = playerUpdateTimes.getOrDefault(playerId, now);
        double deltaSeconds = currentState == null
                ? 0.0
                : Math.min(Math.max(now - previousUpdateTime, 0L), MAX_SERVER_DELTA_MS) / 1000.0;

        PlayerMovementState nextState = currentState == null
                ? createInitialState(playerId, input, now)
                : copyState(currentState);

        double directionX = finiteOrZero(input.getDirectionX());
        double directionY = finiteOrZero(input.getDirectionY());
        double length = Math.hypot(directionX, directionY);
        if (length > 1.0) {
            directionX /= length;
            directionY /= length;
        }

        double velocityX = directionX * PLAYER_SPEED;
        double velocityY = directionY * PLAYER_SPEED;
        nextState.setX(clamp(nextState.getX() + velocityX * deltaSeconds, 0.0, WORLD_WIDTH));
        nextState.setY(clamp(nextState.getY() + velocityY * deltaSeconds, 0.0, WORLD_HEIGHT));
        nextState.setVx(velocityX);
        nextState.setVy(velocityY);
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

    public Optional<String> removeBySession(WebSocketSession session) {
        sessions.remove(session.getId());
        String playerId = sessionPlayers.remove(session.getId());
        if (playerId != null) {
            playerStates.remove(playerId);
            playerUpdateTimes.remove(playerId);
            return Optional.of(playerId);
        }

        return Optional.empty();
    }

    private Optional<PlayerMovementInput> readMovementInput(Object payload) {
        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.convertValue(payload, PlayerMovementInput.class));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> getPlayerId(PlayerMovementInput input) {
        String playerId = input.getPlayerId();
        if (playerId == null || playerId.isBlank()) {
            playerId = input.getId();
        }

        if (playerId == null || playerId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(playerId);
    }

    private PlayerMovementState createInitialState(String playerId, PlayerMovementInput input, long now) {
        PlayerMovementState state = new PlayerMovementState();
        state.setPlayerId(playerId);
        state.setId(playerId);
        state.setName(input.getName() == null || input.getName().isBlank() ? "Player " + playerId : input.getName());
        state.setX(clamp(finiteOrDefault(input.getX(), SPAWN_X), 0.0, WORLD_WIDTH));
        state.setY(clamp(finiteOrDefault(input.getY(), SPAWN_Y), 0.0, WORLD_HEIGHT));
        state.setAnimation("idle-right");
        state.setSequence(input.getSequence() == null ? 0L : input.getSequence());
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
            return directionX < 0.0 ? "walk-left" : "walk-right";
        }

        return previousAnimation != null && previousAnimation.contains("left") ? "idle-left" : "idle-right";
    }

    private double finiteOrZero(Double value) {
        return finiteOrDefault(value, 0.0);
    }

    private double finiteOrDefault(Double value, double defaultValue) {
        return value == null || value.isNaN() || value.isInfinite() ? defaultValue : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
