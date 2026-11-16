package com.game.socket.world;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Đọc map thật trong src/main/resources/maps cho unit test. */
public final class TestMaps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestMaps() {
    }

    public static MapDefinition load(String mapId) {
        try (InputStream input = TestMaps.class.getResourceAsStream("/maps/" + mapId + ".json")) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath map " + mapId);
            }
            return TiledMapParser.parse(mapId, MAPPER.readTree(input));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MapCatalog catalog() {
        return new MapCatalog(List.of(load("village"), load("meadow")), "village");
    }
}
