package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementState;
import com.game.socket.model.room.MapChangeFailReason;
import com.game.socket.state.PlayerPosition;
import com.game.socket.world.MapCatalog;
import com.game.socket.world.MapDefinition;
import com.game.socket.world.MapPoint;
import com.game.socket.world.PortalDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý các room (mỗi map một {@link MapRoom}, tạo sẵn khi khởi động) và người chơi đang ở room nào.
 * Quyết định chuyển map: client chỉ xin, server kiểm vị trí trong portal rồi mới chuyển.
 *
 * Trong lúc đổi room người chơi có thể vắng mặt ở cả hai room trong chốc lát:
 * publisher định kỳ chỉ bỏ lỡ một lượt, chấp nhận được.
 */
@Slf4j
@Component
public class RoomManager {

    private final MapCatalog mapCatalog;
    private final Map<String, MapRoom> rooms;
    /** playerId -> mapId của room đang chứa người chơi. */
    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();

    public RoomManager(MapCatalog mapCatalog) {
        this.mapCatalog = mapCatalog;
        Map<String, MapRoom> created = new LinkedHashMap<>();
        for (MapDefinition definition : mapCatalog.all()) {
            created.put(definition.id(), new MapRoom(definition));
        }
        this.rooms = Collections.unmodifiableMap(created);
    }

    public Optional<MapRoom> room(String mapId) {
        return Optional.ofNullable(mapId == null ? null : rooms.get(mapId));
    }

    public Collection<MapRoom> rooms() {
        return rooms.values();
    }

    public Optional<MapRoom> roomOf(String playerId) {
        String mapId = playerId == null ? null : playerRooms.get(playerId);
        return room(mapId);
    }

    public boolean isInRoom(String playerId) {
        return roomOf(playerId).isPresent();
    }

    /**
     * Đưa người chơi vào game.
     * Đã có trong room (tải lại trang) → giữ room/vị trí, reset sequence; savedPosition bị bỏ qua.
     * Chưa có → vào room theo vị trí đã lưu, đã chuẩn hoá bằng {@link #resolveSpawn(PlayerPosition)}.
     *
     * @param savedPosition vị trí đã lưu (recentLeaves hoặc character-service), null nếu không có
     */
    public JoinResult join(String playerId, String name, PlayerPosition savedPosition, long now) {
        Optional<MapRoom> current = roomOf(playerId);
        if (current.isPresent()) {
            Optional<PlayerMovementState> state = current.get().rejoin(playerId, name, now);
            if (state.isPresent()) {
                return new JoinResult(current.get(), state.get(), true);
            }
        }

        SpawnLocation spawn = resolveSpawn(savedPosition);
        MapRoom room = rooms.get(spawn.map().id());
        PlayerMovementState state = room.enter(playerId, name, spawn.point().x(), spawn.point().y(), now);
        playerRooms.put(playerId, room.id());
        return new JoinResult(room, state, false);
    }

    /**
     * Xin chuyển map qua portal tên portalName.
     * Kiểm: đã join, fromMapId == room hiện tại, portal có trong map, vị trí server lúc now nằm trong
     * vùng portal nới {@link MapDefinition#PORTAL_TOLERANCE}. Không kiểm hướng đi (client đã kiểm).
     */
    public MapChangeResult changeMap(String playerId, String fromMapId, String portalName, long now) {
        String currentMapId = playerId == null ? null : playerRooms.get(playerId);
        MapRoom room = currentMapId == null ? null : rooms.get(currentMapId);
        if (room == null) {
            return MapChangeResult.failed(MapChangeFailReason.NOT_JOINED, null);
        }
        if (!currentMapId.equals(fromMapId)) {
            return MapChangeResult.failed(MapChangeFailReason.WRONG_MAP, currentMapId);
        }

        Optional<PortalDefinition> portalOptional = room.definition().findPortal(portalName);
        if (portalOptional.isEmpty()) {
            return MapChangeResult.failed(MapChangeFailReason.UNKNOWN_PORTAL, currentMapId);
        }
        PortalDefinition portal = portalOptional.get();

        Optional<PlayerPosition> position = room.positionAt(playerId, now);
        if (position.isEmpty()) {
            return MapChangeResult.failed(MapChangeFailReason.NOT_JOINED, currentMapId);
        }
        if (!portal.contains(position.get().x(), position.get().y(), MapDefinition.PORTAL_TOLERANCE)) {
            return MapChangeResult.failed(MapChangeFailReason.NOT_IN_PORTAL, currentMapId);
        }

        // MapCatalog đã kiểm khi khởi động: map đích và spawn đích luôn tồn tại.
        MapRoom target = rooms.get(portal.targetMap());
        MapPoint arrival = target.definition().spawn(portal.targetSpawn())
                .orElseGet(() -> target.definition().defaultSpawn());
        String name = room.getState(playerId).map(PlayerMovementState::getName).orElse(null);

        PlayerPosition leftPosition = room.leave(playerId, now).orElse(position.get());
        PlayerMovementState state = target.enter(playerId, name, arrival.x(), arrival.y(), now);
        playerRooms.put(playerId, target.id());

        log.debug("Player {} moved {} -> {} via portal '{}'", playerId, currentMapId, target.id(), portal.name());
        return MapChangeResult.success(currentMapId, leftPosition, target, state);
    }

    /** Người chơi rời game. Trả về vị trí cuối (có mapId) nếu người chơi đang ở một room. */
    public Optional<PlayerPosition> leave(String playerId, long now) {
        String mapId = playerId == null ? null : playerRooms.remove(playerId);
        MapRoom room = mapId == null ? null : rooms.get(mapId);
        return room == null ? Optional.empty() : room.leave(playerId, now);
    }

    /** Vị trí hiện tại của mọi người chơi ở mọi room (có mapId), dùng để lưu định kỳ. */
    public List<PlayerPosition> currentPositions(long now) {
        List<PlayerPosition> positions = new ArrayList<>();
        rooms.values().forEach(room -> positions.addAll(room.currentPositions(now)));
        return positions;
    }

    public List<PlayerPosition> currentPositions() {
        return currentPositions(System.currentTimeMillis());
    }

    /**
     * Chuẩn hoá vị trí đã lưu thành map + điểm xuất hiện:
     * 1. Không có vị trí đã lưu → map mặc định, spawn default.
     * 2. mapId null (dữ liệu cũ, trước khi lưu map: khi đó chỉ có làng online) → map mặc định, giữ x/y.
     *    mapId không còn trong danh sách map → map mặc định, bỏ x/y (toạ độ của map khác vô nghĩa).
     * 3. x/y thiếu, không hữu hạn hoặc ngoài map → spawn default của map.
     * 4. x/y nằm trong vùng portal (đã nới) → spawn gần nhất, tránh vừa vào đã đứng trong portal.
     */
    SpawnLocation resolveSpawn(PlayerPosition saved) {
        MapDefinition defaultMap = mapCatalog.defaultMap();
        if (saved == null) {
            return new SpawnLocation(defaultMap, defaultMap.defaultSpawn());
        }

        MapDefinition map;
        if (saved.mapId() == null) {
            map = defaultMap;
        } else {
            Optional<MapDefinition> known = mapCatalog.get(saved.mapId());
            if (known.isEmpty()) {
                log.warn("Saved map '{}' of player {} does not exist, using default map '{}'",
                        saved.mapId(), saved.playerId(), defaultMap.id());
                return new SpawnLocation(defaultMap, defaultMap.defaultSpawn());
            }
            map = known.get();
        }

        if (!saved.hasPosition() || !map.contains(saved.x(), saved.y())) {
            return new SpawnLocation(map, map.defaultSpawn());
        }
        if (map.isInAnyPortal(saved.x(), saved.y(), MapDefinition.PORTAL_TOLERANCE)) {
            return new SpawnLocation(map, map.nearestSpawn(saved.x(), saved.y()));
        }
        return new SpawnLocation(map, new MapPoint(saved.x(), saved.y()));
    }

    record SpawnLocation(MapDefinition map, MapPoint point) {
    }
}
