package com.game.socket.constant;

public class SocketEventConstant {
    /** C→S: vào game (bind session ↔ player). Server trả MAP_ENTER. */
    public static final String PLAYER_JOIN = "PLAYER_JOIN";
    /** C→S: input di chuyển; S→C (room): state người chơi. */
    public static final String PLAYER_MOVE = "PLAYER_MOVE";
    /** S→C (room): playerId rời room (ngắt kết nối hoặc đổi map). */
    public static final String PLAYER_LEAVE = "PLAYER_LEAVE";
    /** S→C (chỉ người đó): đã vào map (sau PLAYER_JOIN hoặc MAP_CHANGE thành công). */
    public static final String MAP_ENTER = "MAP_ENTER";
    /** C→S: xin chuyển map qua portal. */
    public static final String MAP_CHANGE = "MAP_CHANGE";
    /** S→C (chỉ người đó): từ chối MAP_CHANGE. */
    public static final String MAP_CHANGE_FAILED = "MAP_CHANGE_FAILED";
}
