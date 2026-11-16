package com.game.socket.model.room;

import lombok.Data;

/** MAP_CHANGE (C→S): xin chuyển map qua portal. */
@Data
public class MapChangeInput {
    /** Map client đang đứng. */
    private String mapId;
    /** Tên portal (object name trong Tiled). */
    private String portal;
}
