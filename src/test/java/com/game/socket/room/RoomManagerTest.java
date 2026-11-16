package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementInput;
import com.game.socket.model.movement.PlayerMovementState;
import com.game.socket.model.room.MapChangeFailReason;
import com.game.socket.state.PlayerPosition;
import com.game.socket.world.TestMaps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomManagerTest {

    private static final String VILLAGE_PORTAL = "Ra đồng cỏ";
    private static final String MEADOW_PORTAL = "Về làng";

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager = new RoomManager(TestMaps.catalog());
    }

    private static PlayerPosition saved(String mapId, double x, double y) {
        return new PlayerPosition("p", mapId, x, y, 0);
    }

    private PlayerMovementState joinAt(String mapId, double x, double y) {
        return roomManager.join("p", "P", saved(mapId, x, y), 0).self();
    }

    // ---- join: chuẩn hoá vị trí đã lưu ----

    @Test
    void joinWithoutSavedPositionUsesDefaultMapAndSpawn() {
        JoinResult result = roomManager.join("p", "P", null, 0);

        assertThat(result.room().id()).isEqualTo("village");
        assertThat(result.self().getX()).isEqualTo(1200.0);
        assertThat(result.self().getY()).isEqualTo(1224.0);
        assertThat(result.rejoined()).isFalse();
        assertThat(roomManager.roomOf("p")).map(MapRoom::id).contains("village");
    }

    @Test
    void joinWithNullMapIdGoesToDefaultMap() {
        PlayerMovementState state = joinAt(null, 500, 600);

        assertThat(state.getMapId()).isEqualTo("village");
        // Dữ liệu cũ (chỉ có làng online) giữ toạ độ nếu nằm trong làng.
        assertThat(state.getX()).isEqualTo(500.0);
        assertThat(state.getY()).isEqualTo(600.0);
    }

    @Test
    void joinWithNullMapIdOutsideVillageUsesDefaultSpawn() {
        // Dữ liệu từ "world 6000x6000" cũ.
        PlayerMovementState state = joinAt(null, 5000, 5000);

        assertThat(state.getMapId()).isEqualTo("village");
        assertThat(state.getX()).isEqualTo(1200.0);
        assertThat(state.getY()).isEqualTo(1224.0);
    }

    @Test
    void joinWithUnknownMapIdUsesDefaultMapAndDropsPosition() {
        PlayerMovementState state = joinAt("abc", 500, 600);

        assertThat(state.getMapId()).isEqualTo("village");
        assertThat(state.getX()).isEqualTo(1200.0);
        assertThat(state.getY()).isEqualTo(1224.0);
    }

    @Test
    void joinRestoresSavedMapAndPosition() {
        PlayerMovementState state = joinAt("meadow", 4000, 4500);

        assertThat(state.getMapId()).isEqualTo("meadow");
        assertThat(state.getX()).isEqualTo(4000.0);
        assertThat(state.getY()).isEqualTo(4500.0);
    }

    @Test
    void joinOutsideMapUsesDefaultSpawnOfThatMap() {
        PlayerMovementState state = joinAt("meadow", 7000, 100);

        assertThat(state.getMapId()).isEqualTo("meadow");
        assertThat(state.getX()).isEqualTo(3000.0);
        assertThat(state.getY()).isEqualTo(3000.0);
    }

    @Test
    void joinWithoutCoordinatesUsesDefaultSpawnOfSavedMap() {
        PlayerMovementState state = roomManager.join("p", "P", new PlayerPosition("p", "meadow", null, null, 0), 0).self();

        assertThat(state.getMapId()).isEqualTo("meadow");
        assertThat(state.getX()).isEqualTo(3000.0);
    }

    @Test
    void joinInsidePortalUsesNearestSpawn() {
        PlayerMovementState village = joinAt("village", 1200, 20);
        assertThat(village.getX()).isEqualTo(1200.0);
        assertThat(village.getY()).isEqualTo(168.0); // from_meadow
        roomManager.leave("p", 0);

        PlayerMovementState meadow = joinAt("meadow", 2900, 5990);
        assertThat(meadow.getX()).isEqualTo(2934.0);
        assertThat(meadow.getY()).isEqualTo(5880.0); // from_village
    }

    @Test
    void rejoinKeepsRoomAndPosition() {
        joinAt("meadow", 4000, 4500);

        JoinResult result = roomManager.join("p", "P", null, 10);
        assertThat(result.rejoined()).isTrue();
        assertThat(result.room().id()).isEqualTo("meadow");
        assertThat(result.self().getX()).isEqualTo(4000.0);
        assertThat(result.self().getSequence()).isZero();
    }

    // ---- changeMap ----

    @Test
    void changeMapMovesPlayerToTargetSpawn() {
        joinAt("village", 1200, 20);
        // 1200,20 nằm trong portal nên join đẩy ra spawn; đi lại vào portal:
        MapRoom village = roomManager.room("village").orElseThrow();
        village.leave("p", 0);
        village.enter("p", "P", 1200, 20, 0);
        PlayerMovementInput input = new PlayerMovementInput();
        input.setSequence(9L);
        village.applyMovement("p", input, 0);
        roomManager.join("q", "Q", saved("village", 600, 600), 0);

        MapChangeResult result = roomManager.changeMap("p", "village", VILLAGE_PORTAL, 10);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.fromMapId()).isEqualTo("village");
        assertThat(result.leftPosition().mapId()).isEqualTo("village");
        assertThat(result.targetRoom().id()).isEqualTo("meadow");
        assertThat(result.state().getMapId()).isEqualTo("meadow");
        assertThat(result.state().getX()).isEqualTo(2934.0);
        assertThat(result.state().getY()).isEqualTo(5880.0);
        assertThat(result.state().getSequence()).isZero();
        assertThat(result.state().getName()).isEqualTo("P");

        assertThat(village.playerIds()).containsExactly("q");
        assertThat(roomManager.room("meadow").orElseThrow().playerIds()).containsExactly("p");
        assertThat(roomManager.roomOf("p")).map(MapRoom::id).contains("meadow");
    }

    @Test
    void changeMapBackFromMeadow() {
        MapRoom meadow = roomManager.room("meadow").orElseThrow();
        joinAt("meadow", 3000, 3000);
        meadow.leave("p", 0);
        meadow.enter("p", "P", 2900, 5990, 0);

        MapChangeResult result = roomManager.changeMap("p", "meadow", MEADOW_PORTAL, 0);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.state().getMapId()).isEqualTo("village");
        assertThat(result.state().getY()).isEqualTo(168.0);
    }

    @Test
    void changeMapWithinToleranceIsAccepted() {
        // Portal làng: y 0..48; nới 48px → tới y=96. Đứng y=90 (ngoài vùng thật, trong dung sai).
        joinAt("village", 600, 600);
        MapRoom village = roomManager.room("village").orElseThrow();
        village.leave("p", 0);
        village.enter("p", "P", 1040, 90, 0);

        assertThat(roomManager.changeMap("p", "village", VILLAGE_PORTAL, 0).succeeded()).isTrue();
    }

    @Test
    void changeMapFromMiddleOfVillageIsRejected() {
        joinAt("village", 1200, 1224);

        MapChangeResult result = roomManager.changeMap("p", "village", VILLAGE_PORTAL, 0);

        assertThat(result.failReason()).isEqualTo(MapChangeFailReason.NOT_IN_PORTAL);
        assertThat(result.fromMapId()).isEqualTo("village");
        assertThat(roomManager.roomOf("p")).map(MapRoom::id).contains("village");
    }

    @Test
    void changeMapJustOutsideToleranceIsRejected() {
        joinAt("village", 600, 600);
        MapRoom village = roomManager.room("village").orElseThrow();
        village.leave("p", 0);
        village.enter("p", "P", 1200, 97, 0);

        assertThat(roomManager.changeMap("p", "village", VILLAGE_PORTAL, 0).failReason())
                .isEqualTo(MapChangeFailReason.NOT_IN_PORTAL);
    }

    @Test
    void changeMapWithWrongMapIsRejected() {
        joinAt("village", 1200, 1224);

        assertThat(roomManager.changeMap("p", "meadow", MEADOW_PORTAL, 0).failReason())
                .isEqualTo(MapChangeFailReason.WRONG_MAP);
        assertThat(roomManager.changeMap("p", null, VILLAGE_PORTAL, 0).failReason())
                .isEqualTo(MapChangeFailReason.WRONG_MAP);
    }

    @Test
    void changeMapWithUnknownPortalIsRejected() {
        joinAt("village", 1200, 1224);

        assertThat(roomManager.changeMap("p", "village", "nope", 0).failReason())
                .isEqualTo(MapChangeFailReason.UNKNOWN_PORTAL);
        assertThat(roomManager.changeMap("p", "village", null, 0).failReason())
                .isEqualTo(MapChangeFailReason.UNKNOWN_PORTAL);
    }

    @Test
    void changeMapBeforeJoinIsRejected() {
        MapChangeResult result = roomManager.changeMap("p", "village", VILLAGE_PORTAL, 0);

        assertThat(result.failReason()).isEqualTo(MapChangeFailReason.NOT_JOINED);
        assertThat(result.fromMapId()).isNull();
    }

    // ---- leave / currentPositions ----

    @Test
    void leaveReturnsPositionWithMapId() {
        joinAt("meadow", 4000, 4500);

        assertThat(roomManager.leave("p", 0)).hasValueSatisfying(p -> {
            assertThat(p.mapId()).isEqualTo("meadow");
            assertThat(p.x()).isEqualTo(4000.0);
        });
        assertThat(roomManager.isInRoom("p")).isFalse();
        assertThat(roomManager.leave("p", 0)).isEmpty();
    }

    @Test
    void currentPositionsCoversAllRooms() {
        roomManager.join("a", "A", saved("village", 600, 600), 0);
        roomManager.join("b", "B", saved("meadow", 4000, 4000), 0);

        assertThat(roomManager.currentPositions(0))
                .extracting(PlayerPosition::mapId)
                .containsExactlyInAnyOrder("village", "meadow");
    }
}
