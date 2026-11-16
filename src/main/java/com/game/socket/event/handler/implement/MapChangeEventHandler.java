package com.game.socket.event.handler.implement;

import com.game.socket.constant.SocketEventConstant;
import com.game.socket.event.handler.SocketEventHandler;
import com.game.socket.event.handler.SocketPayloadReader;
import com.game.socket.kafka.CharacterPositionPublisher;
import com.game.socket.model.room.MapChangeFailReason;
import com.game.socket.model.room.MapChangeFailedPayload;
import com.game.socket.model.room.MapChangeInput;
import com.game.socket.model.room.MapEnterPayload;
import com.game.socket.room.MapChangeResult;
import com.game.socket.room.MapRoom;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerSessionRegistry;
import com.game.socket.websocket.SocketMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

/**
 * MAP_CHANGE: client xin qua portal, server kiểm vị trí rồi quyết định (xem RoomManager.changeMap).
 * Thành công: PLAYER_LEAVE cho room cũ → MAP_ENTER cho người chơi → PLAYER_MOVE cho người khác ở room mới
 * → lưu ngay vị trí mới qua Kafka (reason MAP_CHANGE).
 * Thất bại: MAP_CHANGE_FAILED, người chơi vẫn ở room cũ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapChangeEventHandler implements SocketEventHandler {

    private final SocketPayloadReader payloadReader;
    private final PlayerSessionRegistry sessionRegistry;
    private final RoomManager roomManager;
    private final SocketMessageSender messageSender;
    private final CharacterPositionPublisher positionPublisher;

    @Override
    public String getEvent() {
        return SocketEventConstant.MAP_CHANGE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        MapChangeInput input = payloadReader.read(payload, MapChangeInput.class).orElseGet(MapChangeInput::new);

        Optional<String> playerIdOptional = sessionRegistry.getPlayerId(session);
        if (playerIdOptional.isEmpty()) {
            // Chưa join thì server không giữ map nào: trả lại mapId client gửi để payload luôn có mapId.
            sendFailed(session, input.getMapId(), MapChangeFailReason.NOT_JOINED);
            return;
        }
        String playerId = playerIdOptional.get();

        MapChangeResult result = roomManager.changeMap(
                playerId, input.getMapId(), input.getPortal(), System.currentTimeMillis());
        if (!result.succeeded()) {
            log.info("Rejected MAP_CHANGE of {} from {} via '{}': {}",
                    playerId, input.getMapId(), input.getPortal(), result.failReason());
            String mapId = result.fromMapId() != null ? result.fromMapId() : input.getMapId();
            sendFailed(session, mapId, result.failReason());
            return;
        }

        MapRoom target = result.targetRoom();
        messageSender.broadcastToRoom(result.fromMapId(), SocketEventConstant.PLAYER_LEAVE, playerId);
        messageSender.send(session, SocketEventConstant.MAP_ENTER, new MapEnterPayload(
                target.id(),
                target.definition().width(),
                target.definition().height(),
                result.state(),
                target.snapshotExcept(playerId)
        ));
        messageSender.broadcastToRoom(target.id(), playerId, SocketEventConstant.PLAYER_MOVE, result.state());

        // Gửi Kafka sau cùng: nếu Kafka chậm (tối đa max.block.ms) thì người chơi không phải chờ màn hình đen.
        target.positionAt(playerId, result.state().getServerTime())
                .ifPresent(positionPublisher::publishOnMapChange);
    }

    private void sendFailed(WebSocketSession session, String mapId, MapChangeFailReason reason) {
        messageSender.send(session, SocketEventConstant.MAP_CHANGE_FAILED,
                new MapChangeFailedPayload(mapId == null ? "" : mapId, reason));
    }
}
