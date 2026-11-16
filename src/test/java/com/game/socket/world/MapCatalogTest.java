package com.game.socket.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapCatalogTest {

    private static PortalDefinition portal(String target, String spawn) {
        return new PortalDefinition("p", 0, 0, 96, 48, target, spawn, "north");
    }

    private static MapDefinition map(String id, Map<String, MapPoint> spawns, List<PortalDefinition> portals) {
        return new MapDefinition(id, 1000, 1000, spawns, portals);
    }

    @Test
    void realMapsAreValid() {
        MapCatalog catalog = TestMaps.catalog();

        assertThat(catalog.defaultMap().id()).isEqualTo("village");
        assertThat(catalog.get("meadow")).isPresent();
        assertThat(catalog.get("abc")).isEmpty();
        assertThat(catalog.get(null)).isEmpty();
    }

    @Test
    void missingDefaultSpawnFails() {
        MapDefinition a = map("a", Map.of("other", new MapPoint(500, 500)), List.of());
        assertThatThrownBy(() -> new MapCatalog(List.of(a), "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default");
    }

    @Test
    void unknownDefaultMapFails() {
        MapDefinition a = map("a", Map.of("default", new MapPoint(500, 500)), List.of());
        assertThatThrownBy(() -> new MapCatalog(List.of(a), "b")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void portalToUnknownMapFails() {
        MapDefinition a = map("a", Map.of("default", new MapPoint(500, 500)), List.of(portal("zzz", "default")));
        assertThatThrownBy(() -> new MapCatalog(List.of(a), "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown map");
    }

    @Test
    void portalToUnknownSpawnFails() {
        MapDefinition a = map("a", Map.of("default", new MapPoint(500, 500)), List.of(portal("b", "nope")));
        MapDefinition b = map("b", Map.of("default", new MapPoint(500, 500)), List.of());
        assertThatThrownBy(() -> new MapCatalog(List.of(a, b), "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown spawn");
    }

    @Test
    void arrivalSpawnInsidePortalAreaFails() {
        // b có portal (0,0,96,48); spawn "arrive" ở y=80 nằm trong vùng nới 48px (tới y=96).
        MapDefinition a = map("a", Map.of("default", new MapPoint(500, 500)), List.of(portal("b", "arrive")));
        MapDefinition b = map("b",
                Map.of("default", new MapPoint(500, 500), "arrive", new MapPoint(50, 80)),
                List.of(portal("a", "default")));
        assertThatThrownBy(() -> new MapCatalog(List.of(a, b), "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside a portal");
    }

    @Test
    void invalidMapIdFails() {
        MapDefinition a = map("Bad Id", Map.of("default", new MapPoint(500, 500)), List.of());
        assertThatThrownBy(() -> new MapCatalog(List.of(a), "Bad Id")).isInstanceOf(IllegalStateException.class);
    }
}
