package com.example.funfridaygame.sdk;

/** Thrown by game logic to reject an action; the message is shown to the sender. */
public class InvalidActionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidActionException(String message) {
        super(message);
    }
}
