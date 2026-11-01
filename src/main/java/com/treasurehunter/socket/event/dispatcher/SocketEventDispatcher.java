package com.treasurehunter.socket.event.dispatcher;

import com.treasurehunter.socket.model.request.SocketRequestDTO;
import com.treasurehunter.socket.event.handler.SocketEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SocketEventDispatcher {

    private final Map<String, SocketEventHandler> handlerMap;

    public SocketEventDispatcher(List<SocketEventHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        SocketEventHandler::getEvent,
                        Function.identity()
                ));
        log.info("Registered socket event handlers: {}", handlerMap.keySet());
    }

    public void dispatch(WebSocketSession session, SocketRequestDTO request) {
        log.debug("Dispatching event: {}, payload: {}", request.getEvent(), request.getPayload());
        SocketEventHandler handler = handlerMap.get(request.getEvent());

        if (handler == null) {
            log.warn("Ignored unsupported event '{}' from session {}", request.getEvent(), session.getId());
            return;
        }

        handler.handle(session, request.getPayload());
    }
}
