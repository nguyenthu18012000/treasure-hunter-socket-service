package com.game.socket.room;

import com.game.socket.model.movement.PlayerMovementInput;
import com.game.socket.model.movement.PlayerMovementState;
import com.game.socket.state.PlayerPosition;
import com.game.socket.world.TestMaps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapRoomTest {

    private static PlayerMovementInput input(double dx, double dy, long sequence) {
        PlayerMovementInput input = new PlayerMovementInput();
        input.setDirectionX(dx);
        input.setDirectionY(dy);
        input.setSequence(sequence);
        return input;
    }

    /** Đi sang phải liên tục, mỗi input cách nhau 200ms (MAX_SERVER_DELTA_MS) → 32px/lần. */
    private static PlayerMovementState walkRight(MapRoom room, String playerId, long start, int steps) {
        PlayerMovementState state = null;
        for (int i = 0; i < steps; i++) {
            state = room.applyMovement(playerId, input(1, 0, i + 1), start + i * MapRoom.MAX_SERVER_DELTA_MS).orElseThrow();
        }
        return state;
    }

    @Test
    void clampsToEachMapSize() {
        MapRoom village = new MapRoom(TestMaps.load("village"));
        MapRoom meadow = new MapRoom(TestMaps.load("meadow"));
        village.enter("p", "P", 2300, 1000, 0);
        meadow.enter("p", "P", 4900, 3000, 0);

        // 10 bước * 32px = 320px sang phải
        PlayerMovementState inVillage = walkRight(village, "p", 0, 11);
        PlayerMovementState inMeadow = walkRight(meadow, "p", 0, 11);

        assertThat(inVillage.getX()).isEqualTo(2400.0);
        assertThat(inMeadow.getX()).isEqualTo(5220.0);
        assertThat(inMeadow.getX()).isGreaterThan(5000.0);
        assertThat(inMeadow.getMapId()).isEqualTo("meadow");
    }

    @Test
    void enterSetsMapAndStartsIdle() {
        MapRoom room = new MapRoom(TestMaps.load("meadow"));
        PlayerMovementState state = room.enter("p", " ", 100, 200, 5);

        assertThat(state.getMapId()).isEqualTo("meadow");
        assertThat(state.getName()).isEqualTo("Player p");
        assertThat(state.getSequence()).isZero();
        assertThat(state.getVx()).isZero();
        assertThat(room.playerIds()).containsExactly("p");
    }

    @Test
    void positionAtFollowsLastVelocity() {
        MapRoom room = new MapRoom(TestMaps.load("village"));
        room.enter("p", "P", 1000, 1000, 0);
        room.applyMovement("p", input(1, 0, 1), 1000);

        PlayerPosition position = room.positionAt("p", 1100).orElseThrow();
        assertThat(position.x()).isEqualTo(1016.0);
        assertThat(position.mapId()).isEqualTo("village");
    }

    @Test
    void rejoinKeepsPositionAndResetsSequence() {
        MapRoom room = new MapRoom(TestMaps.load("village"));
        room.enter("p", "P", 1000, 1000, 0);
        room.applyMovement("p", input(1, 0, 42), 1000);

        PlayerMovementState state = room.rejoin("p", null, 1100).orElseThrow();
        assertThat(state.getSequence()).isZero();
        assertThat(state.getX()).isEqualTo(1016.0);
        assertThat(state.getVx()).isZero();
        assertThat(state.getName()).isEqualTo("P");
    }

    @Test
    void snapshotExcludesSelfAndLeaveRemoves() {
        MapRoom room = new MapRoom(TestMaps.load("village"));
        room.enter("a", "A", 100, 100, 0);
        room.enter("b", "B", 200, 200, 0);

        assertThat(room.snapshotExcept("a")).extracting(PlayerMovementState::getPlayerId).containsExactly("b");
        assertThat(room.leave("a", 0)).hasValueSatisfying(p -> assertThat(p.x()).isEqualTo(100.0));
        assertThat(room.playerIds()).containsExactly("b");
        assertThat(room.applyMovement("a", input(1, 0, 1), 10)).isEmpty();
    }
}
