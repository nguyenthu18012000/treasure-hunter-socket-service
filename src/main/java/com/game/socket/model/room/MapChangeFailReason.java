package com.game.socket.model.room;

/** Lý do từ chối MAP_CHANGE (gửi cho client dạng chuỗi). */
public enum MapChangeFailReason {
    /** Chưa gửi PLAYER_JOIN (session chưa gắn với người chơi nào trong room). */
    NOT_JOINED,
    /** mapId client gửi khác room server đang giữ người chơi. */
    WRONG_MAP,
    /** Không có portal tên đó trong map hiện tại. */
    UNKNOWN_PORTAL,
    /** Vị trí trên server không nằm trong vùng portal (đã nới PORTAL_TOLERANCE). */
    NOT_IN_PORTAL
}
