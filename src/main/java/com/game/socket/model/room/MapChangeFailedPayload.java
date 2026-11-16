package com.game.socket.model.room;

/**
 * MAP_CHANGE_FAILED (S→C, chỉ người đó).
 *
 * @param mapId map server đang giữ người chơi
 */
public record MapChangeFailedPayload(String mapId, MapChangeFailReason reason) {
}
