package com.game.socket.world;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hình học của một map mà server cần: kích thước, điểm xuất hiện, portal.
 * Đọc từ Tiled JSON (xem {@link TiledMapParser}), không chép tay số liệu.
 *
 * @param width  chiều rộng (pixel)
 * @param height chiều cao (pixel)
 */
public record MapDefinition(
        String id,
        double width,
        double height,
        Map<String, MapPoint> spawns,
        List<PortalDefinition> portals
) {

    public static final String DEFAULT_SPAWN = "default";

    /**
     * Dung sai khi kiểm người chơi có đứng trong portal không (1 tile mỗi phía).
     * Client dự đoán trước server một khoảng trễ mạng; 48px ~ 300ms ở tốc độ 160px/s.
     */
    public static final double PORTAL_TOLERANCE = 48.0;

    public MapDefinition {
        spawns = Map.copyOf(spawns);
        portals = List.copyOf(portals);
    }

    public boolean contains(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y)
                && x >= 0.0 && x <= width && y >= 0.0 && y <= height;
    }

    /** Spawn "default". MapCatalog bảo đảm luôn có khi khởi động. */
    public MapPoint defaultSpawn() {
        MapPoint spawn = spawns.get(DEFAULT_SPAWN);
        if (spawn == null) {
            throw new IllegalStateException("Map '" + id + "' has no '" + DEFAULT_SPAWN + "' spawn");
        }
        return spawn;
    }

    public Optional<MapPoint> spawn(String name) {
        return Optional.ofNullable(name == null ? null : spawns.get(name));
    }

    /** Spawn gần (x, y) nhất. Dùng khi vị trí đã lưu nằm trong portal. */
    public MapPoint nearestSpawn(double x, double y) {
        return spawns.values().stream()
                .min(Comparator.comparingDouble(p -> Math.hypot(p.x() - x, p.y() - y)))
                .orElseGet(this::defaultSpawn);
    }

    public Optional<PortalDefinition> findPortal(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return portals.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public boolean isInside(PortalDefinition portal, double x, double y, double tolerance) {
        return portal.contains(x, y, tolerance);
    }

    /** true khi (x, y) nằm trong vùng (đã nới) của bất kỳ portal nào. */
    public boolean isInAnyPortal(double x, double y, double tolerance) {
        return portals.stream().anyMatch(p -> p.contains(x, y, tolerance));
    }
}
