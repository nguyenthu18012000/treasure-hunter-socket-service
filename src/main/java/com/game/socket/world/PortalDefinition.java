package com.game.socket.world;

/**
 * Vùng chuyển map (object trong layer "portals" của Tiled).
 * x, y là góc trên trái, đơn vị pixel.
 *
 * @param name        tên object trong Tiled, client gửi lên trong MAP_CHANGE.portal
 * @param targetMap   id map đích
 * @param targetSpawn tên spawn ở map đích
 * @param direction   hướng đi vào portal (client dùng, server không kiểm)
 */
public record PortalDefinition(
        String name,
        double x,
        double y,
        double width,
        double height,
        String targetMap,
        String targetSpawn,
        String direction
) {

    /** true khi (px, py) nằm trong vùng portal đã nới thêm tolerance mỗi phía. */
    public boolean contains(double px, double py, double tolerance) {
        return px >= x - tolerance && px <= x + width + tolerance
                && py >= y - tolerance && py <= y + height + tolerance;
    }
}
