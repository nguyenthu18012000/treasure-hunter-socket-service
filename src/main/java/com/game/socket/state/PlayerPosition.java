package com.game.socket.state;

/**
 * Vị trí của một người chơi tại một thời điểm.
 * mapId là null khi không rõ map (dữ liệu cũ chưa lưu map).
 * x/y là null khi chưa có vị trí (ví dụ character-service chưa lưu vị trí nào).
 */
public record PlayerPosition(String playerId, String mapId, Double x, Double y, long time) {

    public boolean hasPosition() {
        return x != null && y != null;
    }
}
