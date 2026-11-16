package com.game.socket.kafka;

import com.game.common.kafka.KafkaTopics;
import com.game.common.kafka.event.character.CharacterPositionChangedEvent;
import com.game.common.kafka.event.character.CharacterPositionChangedEvent.PositionSaveReason;
import com.game.common.kafka.publisher.KafkaEventPublisher;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerPosition;
import com.game.socket.state.SpawnPositionResolver;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gửi vị trí nhân vật (kèm mapId) sang character-service qua Kafka để lưu vào DB.
 * Vị trí vẫn giữ trong bộ nhớ (các MapRoom), chỉ gửi khi:
 * - PERIODIC: định kỳ, chỉ những người chơi đã di chuyển hoặc đổi map từ lần gửi trước
 * - MAP_CHANGE: vừa qua portal sang map khác (gửi ngay)
 * - DISCONNECT: người chơi rời game
 * - SHUTDOWN: tắt socket-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterPositionPublisher {

    /** Lệch dưới mức này (pixel) coi như đứng yên, không gửi lại. */
    private static final double MIN_DISTANCE_TO_PUBLISH = 0.5;

    private final RoomManager roomManager;
    private final KafkaEventPublisher eventPublisher;
    private final SpawnPositionResolver spawnPositionResolver;

    /** playerId -> vị trí đã gửi lần cuối. */
    private final Map<String, PlayerPosition> lastPublished = new ConcurrentHashMap<>();

    @Scheduled(
            initialDelayString = "${game.position-save.interval-ms:5000}",
            fixedDelayString = "${game.position-save.interval-ms:5000}"
    )
    public void publishChangedPositions() {
        for (PlayerPosition position : roomManager.currentPositions()) {
            if (hasMoved(position)) {
                publish(position, PositionSaveReason.PERIODIC);
            }
        }
    }

    /** Gọi ngay sau khi người chơi qua portal: lưu map mới và vị trí spawn ở map đó. */
    public void publishOnMapChange(PlayerPosition position) {
        publish(position, PositionSaveReason.MAP_CHANGE);
    }

    /** Gọi khi người chơi rời game. Luôn gửi, kể cả khi không di chuyển, để chắc chắn vị trí cuối được lưu. */
    public void publishOnDisconnect(PlayerPosition position) {
        lastPublished.remove(position.playerId());
        publish(position, PositionSaveReason.DISCONNECT);
    }

    /**
     * Khi tắt service: gửi vị trí của người chơi còn online rồi chờ Kafka nhận hết.
     * Thường lúc này Tomcat đã đóng các WebSocket nên danh sách đã rỗng (vị trí được gửi bằng DISCONNECT);
     * đây là lớp bảo vệ thêm.
     */
    @PreDestroy
    public void publishOnShutdown() {
        for (PlayerPosition position : roomManager.currentPositions()) {
            publish(position, PositionSaveReason.SHUTDOWN);
        }
        eventPublisher.flush();
    }

    /** Đổi map thì luôn tính là đã di chuyển, kể cả khi toạ độ trùng với lần gửi trước. */
    boolean hasMoved(PlayerPosition position) {
        PlayerPosition previous = lastPublished.get(position.playerId());
        return previous == null
                || !Objects.equals(previous.mapId(), position.mapId())
                || Math.hypot(position.x() - previous.x(), position.y() - previous.y()) >= MIN_DISTANCE_TO_PUBLISH;
    }

    private void publish(PlayerPosition position, PositionSaveReason reason) {
        if (!position.hasPosition() || !spawnPositionResolver.canSave(position.playerId())) {
            return;
        }

        Optional<UUID> characterId = parseCharacterId(position.playerId());
        if (characterId.isEmpty()) {
            return;
        }

        CharacterPositionChangedEvent event = CharacterPositionChangedEvent.of(
                characterId.get(), position.mapId(), position.x(), position.y(), position.time(), reason);

        // Key = characterId: mọi event của cùng nhân vật vào cùng partition nên được xử lý đúng thứ tự.
        eventPublisher.publish(KafkaTopics.CHARACTER_POSITION_UPDATED, characterId.get().toString(), event);

        if (reason == PositionSaveReason.PERIODIC || reason == PositionSaveReason.MAP_CHANGE) {
            lastPublished.put(position.playerId(), position);
        }
    }

    /** playerId do client gửi lên là id nhân vật (UUID). Id khác (ví dụ dữ liệu giả "1") thì bỏ qua. */
    private Optional<UUID> parseCharacterId(String playerId) {
        try {
            return Optional.of(UUID.fromString(playerId));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Skip saving position: playerId '{}' is not a character id", playerId);
            return Optional.empty();
        }
    }
}
