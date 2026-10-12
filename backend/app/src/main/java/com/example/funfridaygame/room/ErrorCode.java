package com.example.funfridaygame.room;

/** Machine-readable error codes sent to clients in replies. */
public enum ErrorCode {
    ROOM_NOT_FOUND,
    SESSION_EXPIRED,
    NOT_IN_ROOM,
    INVALID_NAME,
    NAME_TAKEN,
    ROOM_FULL,
    TOO_MANY_ROOMS,
    NOT_HOST,
    UNKNOWN_GAME,
    INVALID_SETTINGS,
    NOT_ENOUGH_PLAYERS,
    TOO_MANY_PLAYERS,
    WRONG_PHASE,
    INVALID_ACTION,
    RATE_LIMITED,
    BAD_REQUEST,
    INTERNAL
}
