package com.game.socket.world;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc phần server cần từ file Tiled JSON (giống cách game-ui WorldMap.ts đọc):
 * width/height/tilewidth/tileheight, layer object "spawns" và "portals".
 * Bỏ qua layer tile và "spawn_zones" (để dành cho quái).
 * Dữ liệu thiếu/sai → IllegalStateException để service dừng ngay khi khởi động.
 */
public final class TiledMapParser {

    static final String SPAWNS_LAYER = "spawns";
    static final String PORTALS_LAYER = "portals";

    private TiledMapParser() {
    }

    public static MapDefinition parse(String mapId, JsonNode root) {
        if (root == null || !root.isObject()) {
            throw invalid(mapId, "root is not a JSON object");
        }

        double width = positiveInt(mapId, root, "width") * (double) positiveInt(mapId, root, "tilewidth");
        double height = positiveInt(mapId, root, "height") * (double) positiveInt(mapId, root, "tileheight");

        Map<String, MapPoint> spawns = new LinkedHashMap<>();
        for (JsonNode object : objects(root, SPAWNS_LAYER)) {
            String name = requiredText(mapId, object, "name");
            if (spawns.put(name, new MapPoint(number(mapId, object, "x"), number(mapId, object, "y"))) != null) {
                throw invalid(mapId, "duplicate spawn '" + name + "'");
            }
        }

        List<PortalDefinition> portals = new ArrayList<>();
        for (JsonNode object : objects(root, PORTALS_LAYER)) {
            String name = requiredText(mapId, object, "name");
            Map<String, String> properties = properties(object);
            String targetMap = properties.get("targetMap");
            String targetSpawn = properties.get("targetSpawn");
            if (targetMap == null || targetMap.isBlank() || targetSpawn == null || targetSpawn.isBlank()) {
                throw invalid(mapId, "portal '" + name + "' needs targetMap and targetSpawn");
            }
            portals.add(new PortalDefinition(
                    name,
                    number(mapId, object, "x"),
                    number(mapId, object, "y"),
                    number(mapId, object, "width"),
                    number(mapId, object, "height"),
                    targetMap,
                    targetSpawn,
                    properties.get("direction")
            ));
        }

        return new MapDefinition(mapId, width, height, spawns, portals);
    }

    private static List<JsonNode> objects(JsonNode root, String layerName) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode layer : root.path("layers")) {
            if ("objectgroup".equals(layer.path("type").asText()) && layerName.equals(layer.path("name").asText())) {
                layer.path("objects").forEach(result::add);
            }
        }
        return result;
    }

    /** Tiled lưu custom property dạng mảng [{name, type, value}]. */
    private static Map<String, String> properties(JsonNode object) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode property : object.path("properties")) {
            String name = property.path("name").asText(null);
            JsonNode value = property.get("value");
            if (name != null && value != null && !value.isNull()) {
                result.put(name, value.asText());
            }
        }
        return result;
    }

    private static int positiveInt(String mapId, JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() <= 0) {
            throw invalid(mapId, "'" + field + "' must be a positive integer");
        }
        return value.asInt();
    }

    private static double number(String mapId, JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw invalid(mapId, "object '" + node.path("name").asText() + "' has invalid '" + field + "'");
        }
        return value.asDouble();
    }

    private static String requiredText(String mapId, JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw invalid(mapId, "object without '" + field + "'");
        }
        return value;
    }

    private static IllegalStateException invalid(String mapId, String reason) {
        return new IllegalStateException("Invalid map '" + mapId + "': " + reason);
    }
}
