package com.treasurehunter.socket.state;

/**
 * Vị trí của một người chơi tại một thời điểm.
 * x/y là null khi người chơi rời game trước khi có vị trí nào (chưa từng gửi PLAYER_MOVE hợp lệ).
 */
public record PlayerPosition(String playerId, Double x, Double y, long time) {

    public boolean hasPosition() {
        return x != null && y != null;
    }
}
