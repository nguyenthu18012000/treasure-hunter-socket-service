package com.treasurehunter.socket.event.handler.implement;

import com.treasurehunter.socket.constant.SocketEventConstant;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class MoveEventHandler implements SocketEventHandler {
    @Override
    public String getEvent() {
        return SocketEventConstant.MOVE;
    }

    @Override
    public void handle(WebSocketSession session, Object payload) {
        log.info("Handling MOVE event with payload: {}", payload);
    }
}
