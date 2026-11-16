package com.game.socket.event.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Đổi payload (Map do Jackson đọc) sang class input. Payload sai kiểu → empty, không ném lỗi. */
@Component
public class SocketPayloadReader {

    private final ObjectMapper objectMapper;

    public SocketPayloadReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> read(Object payload, Class<T> type) {
        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(objectMapper.convertValue(payload, type));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
