package com.treasurehunter.socket.model.movement;

import lombok.Data;

@Data
public class PlayerMovementState {
    private String playerId;
    private String id;
    private String name;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private double directionX;
    private double directionY;
    private String animation;
    private long sequence;
    private long serverTime;
}
