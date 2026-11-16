package com.game.socket.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Danh sách map server biết, đọc từ classpath:maps/{id}.json theo cấu hình game.maps.
 * Kiểm tra khi khởi động (fail-fast): map lỗi thì service không chạy, thay vì chạy với portal/spawn sai.
 */
@Slf4j
@Component
public class MapCatalog {

    /** Giống ràng buộc ở character-service (cột map_id varchar(50)). */
    private static final Pattern MAP_ID_PATTERN = Pattern.compile("^[a-z0-9_-]{1,50}$");

    private final Map<String, MapDefinition> maps;
    private final MapDefinition defaultMap;

    @Autowired
    public MapCatalog(
            ObjectMapper objectMapper,
            @Value("${game.maps.ids:village,meadow}") List<String> mapIds,
            @Value("${game.maps.default-map:village}") String defaultMapId
    ) {
        this(loadFromClasspath(objectMapper, mapIds), defaultMapId);
        log.info("Loaded maps: {} (default: {})", maps.keySet(), defaultMap.id());
    }

    public MapCatalog(Collection<MapDefinition> definitions, String defaultMapId) {
        Map<String, MapDefinition> byId = new LinkedHashMap<>();
        for (MapDefinition definition : definitions) {
            if (byId.put(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate map id '" + definition.id() + "'");
            }
        }
        this.maps = Collections.unmodifiableMap(byId);
        validate(maps, defaultMapId);
        this.defaultMap = maps.get(defaultMapId);
    }

    public Optional<MapDefinition> get(String mapId) {
        return Optional.ofNullable(mapId == null ? null : maps.get(mapId));
    }

    public MapDefinition defaultMap() {
        return defaultMap;
    }

    public Collection<MapDefinition> all() {
        return maps.values();
    }

    private static List<MapDefinition> loadFromClasspath(ObjectMapper objectMapper, List<String> mapIds) {
        List<MapDefinition> definitions = new ArrayList<>();
        for (String rawId : mapIds) {
            String mapId = rawId.trim();
            if (mapId.isEmpty()) {
                continue;
            }
            ClassPathResource resource = new ClassPathResource("maps/" + mapId + ".json");
            try (InputStream input = resource.getInputStream()) {
                definitions.add(TiledMapParser.parse(mapId, objectMapper.readTree(input)));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read map file classpath:maps/" + mapId + ".json", e);
            }
        }
        return definitions;
    }

    private static void validate(Map<String, MapDefinition> maps, String defaultMapId) {
        if (maps.isEmpty()) {
            throw new IllegalStateException("No map configured (game.maps.ids)");
        }
        if (defaultMapId == null || !maps.containsKey(defaultMapId)) {
            throw new IllegalStateException("Default map '" + defaultMapId + "' is not in " + maps.keySet());
        }

        for (MapDefinition map : maps.values()) {
            if (!MAP_ID_PATTERN.matcher(map.id()).matches()) {
                throw new IllegalStateException("Map id '" + map.id() + "' must match " + MAP_ID_PATTERN.pattern());
            }
            MapPoint defaultSpawn = map.spawns().get(MapDefinition.DEFAULT_SPAWN);
            if (defaultSpawn == null) {
                throw new IllegalStateException("Map '" + map.id() + "' has no '" + MapDefinition.DEFAULT_SPAWN + "' spawn");
            }
            requireArrivalOutsidePortals(map, MapDefinition.DEFAULT_SPAWN, defaultSpawn);

            for (PortalDefinition portal : map.portals()) {
                MapDefinition target = maps.get(portal.targetMap());
                if (target == null) {
                    throw new IllegalStateException("Portal '" + portal.name() + "' of map '" + map.id()
                            + "' targets unknown map '" + portal.targetMap() + "'");
                }
                MapPoint arrival = target.spawns().get(portal.targetSpawn());
                if (arrival == null) {
                    throw new IllegalStateException("Portal '" + portal.name() + "' of map '" + map.id()
                            + "' targets unknown spawn '" + portal.targetSpawn() + "' in map '" + target.id() + "'");
                }
                requireArrivalOutsidePortals(target, portal.targetSpawn(), arrival);
            }
        }
    }

    /** Spawn đến nằm trong vùng portal (đã nới) thì vừa đến đã có thể bị đẩy ngược lại. */
    private static void requireArrivalOutsidePortals(MapDefinition map, String spawnName, MapPoint spawn) {
        if (!map.contains(spawn.x(), spawn.y())) {
            throw new IllegalStateException("Spawn '" + spawnName + "' of map '" + map.id() + "' is outside the map");
        }
        if (map.isInAnyPortal(spawn.x(), spawn.y(), MapDefinition.PORTAL_TOLERANCE)) {
            throw new IllegalStateException("Spawn '" + spawnName + "' of map '" + map.id()
                    + "' is inside a portal area (tolerance " + MapDefinition.PORTAL_TOLERANCE + "px)");
        }
    }
}
