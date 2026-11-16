package com.game.socket.model.room;

import com.game.socket.model.movement.PlayerMovementState;

import java.util.List;

/**
 * MAP_ENTER (S→C, chỉ người đó): trả lời PLAYER_JOIN và MAP_CHANGE thành công.
 *
 * @param width   chiều rộng map (pixel), client so với map đã tải để phát hiện lệch
 * @param players người khác trong room (snapshot)
 */
public record MapEnterPayload(
        String mapId,
        double width,
        double height,
        PlayerMovementState self,
        List<PlayerMovementState> players
) {
}
