package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementState;

/**
 * Kết quả PLAYER_JOIN.
 *
 * @param rejoined true khi người chơi đã có trong room (tải lại trang), giữ room và vị trí
 */
public record JoinResult(MapRoom room, PlayerMovementState self, boolean rejoined) {
}
