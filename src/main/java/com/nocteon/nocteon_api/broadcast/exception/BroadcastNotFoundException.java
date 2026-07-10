package com.nocteon.nocteon_api.broadcast.exception;

public class BroadcastNotFoundException extends RuntimeException {
    public BroadcastNotFoundException() {
        super("broadcast.notFound");
    }
}