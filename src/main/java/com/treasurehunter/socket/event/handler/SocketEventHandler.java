package com.treasurehunter.socket.event.handler;

import org.springframework.web.socket.WebSocketSession;

public interface SocketEventHandler {
    String getEvent();

    void handle(WebSocketSession session, Object payload);
}
