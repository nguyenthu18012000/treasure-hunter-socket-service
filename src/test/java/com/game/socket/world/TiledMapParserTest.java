package com.game.socket.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TiledMapParserTest {

    @Test
    void parsesVillage() {
        MapDefinition village = TestMaps.load("village");

        assertThat(village.id()).isEqualTo("village");
        assertThat(village.width()).isEqualTo(2400.0);
        assertThat(village.height()).isEqualTo(1920.0);
        assertThat(village.defaultSpawn()).isEqualTo(new MapPoint(1200, 1224));
        assertThat(village.spawn("from_meadow")).contains(new MapPoint(1200, 168));

        assertThat(village.portals()).hasSize(1);
        PortalDefinition portal = village.portals().get(0);
        assertThat(portal.name()).isEqualTo("Ra đồng cỏ");
        assertThat(portal.x()).isEqualTo(1056.0);
        assertThat(portal.y()).isEqualTo(0.0);
        assertThat(portal.width()).isEqualTo(288.0);
        assertThat(portal.height()).isEqualTo(48.0);
        assertThat(portal.targetMap()).isEqualTo("meadow");
        assertThat(portal.targetSpawn()).isEqualTo("from_village");
        assertThat(portal.direction()).isEqualTo("north");
    }

    @Test
    void parsesMeadow() {
        MapDefinition meadow = TestMaps.load("meadow");

        assertThat(meadow.width()).isEqualTo(6000.0);
        assertThat(meadow.height()).isEqualTo(6000.0);
        assertThat(meadow.defaultSpawn()).isEqualTo(new MapPoint(3000, 3000));
        assertThat(meadow.spawn("from_village")).contains(new MapPoint(2934, 5880));
        assertThat(meadow.findPortal("Về làng")).hasValueSatisfying(p -> {
            assertThat(p.targetMap()).isEqualTo("village");
            assertThat(p.targetSpawn()).isEqualTo("from_meadow");
        });
    }

    @Test
    void portalWithoutTargetFails() throws Exception {
        String json = """
                {"width":10,"height":10,"tilewidth":48,"tileheight":48,"layers":[
                  {"type":"objectgroup","name":"spawns","objects":[{"name":"default","x":10,"y":10}]},
                  {"type":"objectgroup","name":"portals","objects":[{"name":"p","x":0,"y":0,"width":48,"height":48}]}
                ]}""";
        assertThatThrownBy(() -> TiledMapParser.parse("x", new ObjectMapper().readTree(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("targetMap");
    }

    @Test
    void missingSizeFails() throws Exception {
        assertThatThrownBy(() -> TiledMapParser.parse("x", new ObjectMapper().readTree("{\"layers\":[]}")))
                .isInstanceOf(IllegalStateException.class);
    }
}
