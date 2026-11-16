package com.game.socket.model.request;

import lombok.Data;

@Data
public class SocketRequestDTO {
    private String event;
    private Object payload;
}
