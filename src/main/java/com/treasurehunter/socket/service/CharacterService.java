package com.treasurehunter.socket.service;

import com.TreasureHunter.CommonLib.constant.CommonConstant;
import com.TreasureHunter.CommonLib.dto.response.character.CharacterRuntimeResponseDTO;
import com.TreasureHunter.CommonLib.dto.response.character.CharacterStatsInfoDTO;
import com.TreasureHunter.CommonLib.response.BaseResponse;
import com.TreasureHunter.CommonLib.service.CharacterFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public CharacterStatsInfoDTO getCharacterStats(Long userId) {
        CharacterRuntimeResponseDTO characterRuntime = getCharacterRuntime(userId);
        CharacterStatsInfoDTO stats = characterRuntime.getStats();

        if (stats == null) {
            throw new IllegalStateException("Character stats not found for userId: " + userId);
        }

        return stats;
    }

    public Double getCharacterSpeed(Long userId) {
        Double speed = getCharacterStats(userId).getSpeed();

        if (speed == null) {
            throw new IllegalStateException("Character speed not found for userId: " + userId);
        }

        return speed;
    }

    private CharacterRuntimeResponseDTO unwrapCharacterRuntime(
            BaseResponse<CharacterRuntimeResponseDTO> response,
            Long userId
    ) {
        if (response == null) {
            throw new IllegalStateException("Empty response from character-service for userId: " + userId);
        }

        if (!CommonConstant.RESPONSE_CODE.SUCCESS.equals(response.getCode())) {
            log.warn(
                    "Failed to get character runtime. userId: {}, code: {}, message: {}",
                    userId,
                    response.getCode(),
                    response.getMessage()
            );
            throw new IllegalStateException("Character service failed: " + response.getMessage());
        }

        CharacterRuntimeResponseDTO data = response.getData();
        if (data == null) {
            throw new IllegalStateException("Character runtime not found for userId: " + userId);
        }

        return data;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
