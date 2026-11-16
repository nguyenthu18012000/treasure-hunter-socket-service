package com.game.socket.kafka;

import com.game.common.kafka.KafkaTopics;
import com.game.common.kafka.event.BaseEvent;
import com.game.common.kafka.event.character.CharacterPositionChangedEvent;
import com.game.common.kafka.event.character.CharacterPositionChangedEvent.PositionSaveReason;
import com.game.common.kafka.publisher.KafkaEventPublisher;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerPosition;
import com.game.socket.state.SpawnPositionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterPositionPublisherTest {

    private final String playerId = UUID.randomUUID().toString();
    private RoomManager roomManager;
    private KafkaEventPublisher eventPublisher;
    private SpawnPositionResolver spawnPositionResolver;
    private CharacterPositionPublisher publisher;

    @BeforeEach
    void setUp() {
        roomManager = mock(RoomManager.class);
        eventPublisher = mock(KafkaEventPublisher.class);
        spawnPositionResolver = mock(SpawnPositionResolver.class);
        when(spawnPositionResolver.canSave(anyString())).thenReturn(true);
        publisher = new CharacterPositionPublisher(roomManager, eventPublisher, spawnPositionResolver);
    }

    private PlayerPosition position(String mapId, double x, double y) {
        return new PlayerPosition(playerId, mapId, x, y, 1L);
    }

    private List<CharacterPositionChangedEvent> sentEvents(int expected) {
        ArgumentCaptor<BaseEvent> captor = ArgumentCaptor.forClass(BaseEvent.class);
        verify(eventPublisher, times(expected))
                .publish(eq(KafkaTopics.CHARACTER_POSITION_UPDATED), eq(playerId), captor.capture());
        return captor.getAllValues().stream().map(CharacterPositionChangedEvent.class::cast).toList();
    }

    @Test
    void periodicEventCarriesMapId() {
        when(roomManager.currentPositions()).thenReturn(List.of(position("meadow", 10, 20)));

        publisher.publishChangedPositions();

        CharacterPositionChangedEvent event = sentEvents(1).get(0);
        assertThat(event.getMapId()).isEqualTo("meadow");
        assertThat(event.getPosX()).isEqualTo(10.0);
        assertThat(event.getReason()).isEqualTo(PositionSaveReason.PERIODIC);
    }

    @Test
    void sameMapSamePositionIsNotResent() {
        when(roomManager.currentPositions()).thenReturn(List.of(position("village", 10, 20)));

        publisher.publishChangedPositions();
        publisher.publishChangedPositions();

        sentEvents(1);
    }

    @Test
    void mapChangeWithSameCoordinatesCountsAsMoved() {
        when(roomManager.currentPositions())
                .thenReturn(List.of(position("village", 10, 20)))
                .thenReturn(List.of(position("meadow", 10, 20)));

        publisher.publishChangedPositions();
        publisher.publishChangedPositions();

        assertThat(sentEvents(2)).extracting(CharacterPositionChangedEvent::getMapId)
                .containsExactly("village", "meadow");
    }

    @Test
    void mapChangeIsPublishedImmediatelyAndNotRepeated() {
        publisher.publishOnMapChange(position("meadow", 2934, 5880));
        when(roomManager.currentPositions()).thenReturn(List.of(position("meadow", 2934, 5880)));
        publisher.publishChangedPositions();

        CharacterPositionChangedEvent event = sentEvents(1).get(0);
        assertThat(event.getReason()).isEqualTo(PositionSaveReason.MAP_CHANGE);
        assertThat(event.getMapId()).isEqualTo("meadow");
    }

    @Test
    void notSavedWhenSavedPositionUnknown() {
        when(spawnPositionResolver.canSave(playerId)).thenReturn(false);

        publisher.publishOnMapChange(position("meadow", 1, 2));

        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }
}
