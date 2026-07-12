package com.treasurehunter.socket.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlayerStateRegistry {

    private final Map<String, Object> playerStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionPlayers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public PlayerStateRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> getPlayerId(Object payload) {
        if (payload == null) {
            return Optional.empty();
        }

        Map<?, ?> payloadMap = objectMapper.convertValue(payload, Map.class);
        Object playerId = payloadMap.get("playerId");
        if (playerId == null) {
            playerId = payloadMap.get("id");
        }

        if (playerId == null || playerId.toString().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(playerId.toString());
    }

    public List<Object> getStatesExcept(String playerId) {
        List<Object> states = new ArrayList<>();
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

    public void update(WebSocketSession session, String playerId, Object state) {
        String previousPlayerId = sessionPlayers.put(session.getId(), playerId);
        if (previousPlayerId != null && !previousPlayerId.equals(playerId)) {
            playerStates.remove(previousPlayerId);
        }

        playerStates.put(playerId, state);
    }

    public Optional<String> removeBySession(WebSocketSession session) {
        String playerId = sessionPlayers.remove(session.getId());
        if (playerId != null) {
            playerStates.remove(playerId);
            return Optional.of(playerId);
        }

        return Optional.empty();
    }
}
