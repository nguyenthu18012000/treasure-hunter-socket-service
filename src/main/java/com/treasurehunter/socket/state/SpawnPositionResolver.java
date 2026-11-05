package com.treasurehunter.socket.state;

import com.TreasureHunter.CommonLib.dto.response.character.CharacterRuntimeResponseDTO;
import com.TreasureHunter.CommonLib.dto.response.character.CharacterStateInfoDTO;
import com.treasurehunter.socket.service.CharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chọn vị trí xuất hiện khi người chơi vào game:
 * 1. Vị trí lúc vừa rời game (giữ trong RECENT_LEAVE_TTL_MS). Vị trí này được gửi qua Kafka bất đồng bộ,
 *    nên nếu người chơi vào lại ngay thì có thể character-service chưa lưu xong, đọc DB sẽ ra vị trí cũ.
 * 2. Vị trí đã lưu ở character-service.
 * 3. Không có: trả về empty, PlayerStateRegistry dùng điểm xuất hiện mặc định.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpawnPositionResolver {

    private static final long RECENT_LEAVE_TTL_MS = 60_000L;

    private final CharacterService characterService;

    /** playerId -> vị trí lúc rời game. */
    private final Map<String, PlayerPosition> recentLeaves = new ConcurrentHashMap<>();

    /**
     * Người chơi không tải được vị trí đã lưu (character-service lỗi). Họ xuất hiện ở vị trí mặc định,
     * nên vị trí của họ KHÔNG được lưu, tránh ghi đè vị trí thật trong DB bằng vị trí mặc định.
     */
    private final Set<String> unknownSavedPosition = ConcurrentHashMap.newKeySet();

    public void rememberLeave(PlayerPosition position) {
        long now = System.currentTimeMillis();
        recentLeaves.values().removeIf(p -> isExpired(p, now));

        if (unknownSavedPosition.remove(position.playerId())) {
            return;
        }
        if (position.hasPosition()) {
            recentLeaves.put(position.playerId(), position);
        }
    }

    public Optional<PlayerPosition> resolve(String playerId) {
        PlayerPosition recent = recentLeaves.remove(playerId);
        if (recent != null && !isExpired(recent, System.currentTimeMillis())) {
            unknownSavedPosition.remove(playerId);
            return Optional.of(recent);
        }
        return loadSavedPosition(playerId);
    }

    /** false khi chưa tải được vị trí đã lưu của người chơi: lưu lúc này sẽ ghi đè vị trí thật. */
    public boolean canSave(String playerId) {
        return !unknownSavedPosition.contains(playerId);
    }

    /**
     * Lỗi khi gọi character-service thì vẫn cho vào game ở vị trí mặc định, không chặn người chơi,
     * nhưng đánh dấu để không lưu vị trí của người chơi này.
     */
    private Optional<PlayerPosition> loadSavedPosition(String playerId) {
        UUID characterId;
        try {
            characterId = UUID.fromString(playerId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        try {
            CharacterRuntimeResponseDTO runtime = characterService.getCharacterRuntimeById(characterId);
            unknownSavedPosition.remove(playerId);
            CharacterStateInfoDTO state = runtime.getState();
            if (state == null || state.getPosX() == null || state.getPosY() == null) {
                return Optional.empty();
            }
            return Optional.of(new PlayerPosition(playerId, state.getPosX(), state.getPosY(), System.currentTimeMillis()));
        } catch (Exception e) {
            unknownSavedPosition.add(playerId);
            log.warn("Could not load saved position of character {}, using default spawn and not saving its position: {}",
                    characterId, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isExpired(PlayerPosition position, long now) {
        return now - position.time() > RECENT_LEAVE_TTL_MS;
    }
}
