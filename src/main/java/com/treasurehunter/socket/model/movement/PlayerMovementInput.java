package com.treasurehunter.socket.model.movement;

import lombok.Data;

@Data
public class PlayerMovementInput {
    private String playerId;
    private String id;
    private String name;
    private Double x;
    private Double y;
    private Double directionX;
    private Double directionY;
    private String animation;
    private Long sequence;
}
