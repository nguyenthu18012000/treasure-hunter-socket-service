package com.game.socket.model.room;

import lombok.Data;

/** PLAYER_JOIN (C→S): gửi 1 lần sau khi kết nối. */
@Data
public class PlayerJoinInput {
    private String playerId;
    private String name;
}
