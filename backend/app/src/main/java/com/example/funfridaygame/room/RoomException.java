package com.example.funfridaygame.room;

/** A rule violation reported back to the client that caused it. */
public class RoomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public RoomException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
