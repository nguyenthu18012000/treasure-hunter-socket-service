package com.treasurehunter.socket.service;

import com.TreasureHunter.CommonLib.constant.CommonConstant;
import com.TreasureHunter.CommonLib.dto.response.character.CharacterRuntimeResponseDTO;
import com.TreasureHunter.CommonLib.constant.StatKey;
import com.TreasureHunter.CommonLib.response.BaseResponse;
import com.TreasureHunter.CommonLib.service.CharacterFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterFeignClient characterFeignClient;

    public CharacterRuntimeResponseDTO getCharacterRuntime(Long userId) {
        validateUserId(userId);

        BaseResponse<CharacterRuntimeResponseDTO> response = characterFeignClient.getCharacterState(userId);
        return unwrapCharacterRuntime(response, userId);
    }

    /** Lấy state đã lưu (vị trí...) theo id nhân vật. Dùng khi người chơi vào game. */
    public CharacterRuntimeResponseDTO getCharacterRuntimeById(UUID characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId is required");
        }

        BaseResponse<CharacterRuntimeResponseDTO> response = characterFeignClient.getCharacterStateById(characterId);
        return unwrapCharacterRuntime(response, characterId);
    }

    /** stat_key -> giá trị, xem StatKey. */
    public Map<String, Double> getCharacterStats(Long userId) {
        CharacterRuntimeResponseDTO characterRuntime = getCharacterRuntime(userId);
        Map<String, Double> stats = characterRuntime.getStats();

        if (stats == null) {
            throw new IllegalStateException("Character stats not found for userId: " + userId);
        }

        return stats;
    }

    public Double getCharacterSpeed(Long userId) {
        Double speed = getCharacterStats(userId).get(StatKey.SPEED);

        if (speed == null) {
            throw new IllegalStateException("Character speed not found for userId: " + userId);
        }

        return speed;
    }

    private CharacterRuntimeResponseDTO unwrapCharacterRuntime(
            BaseResponse<CharacterRuntimeResponseDTO> response,
            Object ownerId
    ) {
        if (response == null) {
            throw new IllegalStateException("Empty response from character-service for " + ownerId);
        }

        if (!CommonConstant.RESPONSE_CODE.SUCCESS.equals(response.getCode())) {
            log.warn(
                    "Failed to get character runtime. id: {}, code: {}, message: {}",
                    ownerId,
                    response.getCode(),
                    response.getMessage()
            );
            throw new IllegalStateException("Character service failed: " + response.getMessage());
        }

        CharacterRuntimeResponseDTO data = response.getData();
        if (data == null) {
            throw new IllegalStateException("Character runtime not found for " + ownerId);
        }

        return data;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
