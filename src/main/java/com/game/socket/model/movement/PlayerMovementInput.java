package com.game.socket.model.movement;

import lombok.Data;

@Data
public class PlayerMovementInput {
    private String playerId;
    private String id;
    private String name;
    /** Map người chơi đang đứng (client gửi). Khác room hiện tại hoặc thiếu thì input bị bỏ qua. */
    private String mapId;
    private Double x;
    private Double y;
    private Double directionX;
    private Double directionY;
    private String animation;
    private Long sequence;
}
