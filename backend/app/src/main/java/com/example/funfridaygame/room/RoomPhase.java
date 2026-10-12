package com.example.funfridaygame.room;

/** Where a room is in its lifecycle. */
public enum RoomPhase {
    /** Players gather, the host picks a game and settings. */
    LOBBY,
    /** Short countdown before the first turn. */
    STARTING,
    /** A turn is being played. */
    PLAYING,
    /** A turn's result is shown. */
    REVEAL,
    /** The podium of the last game is shown. */
    FINISHED
}
