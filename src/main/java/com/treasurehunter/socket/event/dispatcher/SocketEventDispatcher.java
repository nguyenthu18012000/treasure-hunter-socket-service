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
    }

    public void dispatch(WebSocketSession session, SocketRequestDTO request) {
        log.info("Dispatching event: {}, payload: {}", request.getEvent(), request.getPayload());
        log.info("Available handlers: {}", handlerMap.keySet());
        SocketEventHandler handler = handlerMap.get(request.getEvent());

        if (handler == null) {
            throw new RuntimeException("Unsupported event: " + request.getEvent());
        }

        handler.handle(session, request.getPayload());
    }
}
