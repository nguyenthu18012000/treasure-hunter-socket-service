package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementState;
import com.game.socket.model.room.MapChangeFailReason;
import com.game.socket.state.PlayerPosition;

/**
 * Kết quả xin đổi map.
 *
 * @param failReason   null khi thành công
 * @param fromMapId    room người chơi đang ở (thất bại) hoặc vừa rời (thành công); null khi chưa join
 * @param leftPosition vị trí lúc rời room cũ (chỉ khi thành công)
 * @param targetRoom   room mới (chỉ khi thành công)
 * @param state        state ở room mới (chỉ khi thành công)
 */
public record MapChangeResult(
        MapChangeFailReason failReason,
        String fromMapId,
        PlayerPosition leftPosition,
        MapRoom targetRoom,
        PlayerMovementState state
) {

    public static MapChangeResult failed(MapChangeFailReason reason, String currentMapId) {
        return new MapChangeResult(reason, currentMapId, null, null, null);
    }

    public static MapChangeResult success(
            String fromMapId,
            PlayerPosition leftPosition,
            MapRoom targetRoom,
            PlayerMovementState state
    ) {
        return new MapChangeResult(null, fromMapId, leftPosition, targetRoom, state);
    }

    public boolean succeeded() {
        return failReason == null;
    }
}
