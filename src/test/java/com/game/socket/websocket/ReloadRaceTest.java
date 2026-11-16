package com.game.socket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.socket.constant.SocketEventConstant;
import com.game.socket.event.dispatcher.SocketEventDispatcher;
import com.game.socket.event.handler.SocketPayloadReader;
import com.game.socket.event.handler.implement.JoinEventHandler;
import com.game.socket.kafka.CharacterPositionPublisher;
import com.game.socket.room.RoomManager;
import com.game.socket.state.PlayerSessionRegistry;
import com.game.socket.state.SpawnPositionResolver;
import com.game.socket.world.TestMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F5: session cũ (A) đóng, session mới (B) gửi PLAYER_JOIN cùng lúc (QA MINOR #1).
 * Trước khi sửa: A đã bỏ bind nhưng chưa leave → B thấy người chơi còn trong room nên rejoin,
 * nhận MAP_ENTER → A gọi leave → server không còn giữ người chơi.
 */
class ReloadRaceTest {

    private static final String PLAYER = "player-1";
    private static final Map<String, String> JOIN_PAYLOAD = Map.of("playerId", PLAYER, "name", "P");

    private PlayerSessionRegistry sessionRegistry;
    private RoomManager roomManager;
    private SpawnPositionResolver spawnPositionResolver;
    private SocketMessageSender messageSender;
    private JoinEventHandler joinHandler;
    private GameWebSocketHandler socketHandler;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        sessionRegistry = new PlayerSessionRegistry();
        roomManager = spy(new RoomManager(TestMaps.catalog()));
        spawnPositionResolver = mock(SpawnPositionResolver.class);
        when(spawnPositionResolver.resolve(anyString())).thenReturn(Optional.empty());
        messageSender = mock(SocketMessageSender.class);
        joinHandler = new JoinEventHandler(
                new SocketPayloadReader(new ObjectMapper()), sessionRegistry, roomManager, spawnPositionResolver, messageSender);
        socketHandler = new GameWebSocketHandler(
                new ObjectMapper(), mock(SocketEventDispatcher.class), sessionRegistry, roomManager, messageSender,
                mock(CharacterPositionPublisher.class), spawnPositionResolver);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        socketHandler.afterConnectionEstablished(session);
        return session;
    }

    @Test
    void oldSessionClosingWhileNewSessionJoinsKeepsPlayerInRoom() throws Exception {
        WebSocketSession a = session("A");
        joinHandler.handle(a, JOIN_PAYLOAD);
        assertThat(roomManager.isInRoom(PLAYER)).isTrue();

        // Dừng luồng đóng A ngay trong roomManager.leave (sau khi A đã bỏ bind), để B JOIN chen vào đúng lúc đó.
        CountDownLatch leaveEntered = new CountDownLatch(1);
        CountDownLatch releaseLeave = new CountDownLatch(1);
        doAnswer(invocation -> {
            leaveEntered.countDown();
            assertThat(releaseLeave.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(roomManager).leave(eq(PLAYER), anyLong());

        Future<?> closeA = executor.submit(() -> socketHandler.afterConnectionClosed(a, CloseStatus.GOING_AWAY));
        assertThat(leaveEntered.await(5, TimeUnit.SECONDS)).isTrue();

        WebSocketSession b = session("B");
        Future<?> joinB = executor.submit(() -> joinHandler.handle(b, JOIN_PAYLOAD));

        // B phải chờ A xong (cùng khoá theo playerId), không được rejoin giữa chừng.
        Thread.sleep(200);
        assertThat(joinB.isDone()).isFalse();

        releaseLeave.countDown();
        closeA.get(5, TimeUnit.SECONDS);
        joinB.get(5, TimeUnit.SECONDS);

        assertThat(roomManager.isInRoom(PLAYER)).isTrue();
        assertThat(sessionRegistry.getPlayerId(b)).contains(PLAYER);
        verify(messageSender).send(eq(b), eq(SocketEventConstant.MAP_ENTER), any());
    }

    @Test
    void replacedSessionClosingAfterRejoinKeepsPlayerInRoom() {
        WebSocketSession a = session("A");
        joinHandler.handle(a, JOIN_PAYLOAD);
        WebSocketSession b = session("B");
        joinHandler.handle(b, JOIN_PAYLOAD);

        socketHandler.afterConnectionClosed(a, CloseStatus.POLICY_VIOLATION);

        assertThat(roomManager.isInRoom(PLAYER)).isTrue();
        assertThat(sessionRegistry.getPlayerId(b)).contains(PLAYER);
    }

    @Test
    void sessionClosedWhileLoadingSavedPositionDoesNotLeaveGhostPlayer() {
        WebSocketSession a = session("A");
        // Session đóng trong lúc JOIN đang gọi character-service (ngoài khoá).
        when(spawnPositionResolver.resolve(PLAYER)).thenAnswer(invocation -> {
            socketHandler.afterConnectionClosed(a, CloseStatus.GOING_AWAY);
            return Optional.empty();
        });

        joinHandler.handle(a, JOIN_PAYLOAD);

        assertThat(roomManager.isInRoom(PLAYER)).isFalse();
        verify(messageSender, never()).send(eq(a), eq(SocketEventConstant.MAP_ENTER), any());
    }
}
