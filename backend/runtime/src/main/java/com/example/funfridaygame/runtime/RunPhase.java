package com.example.funfridaygame.runtime;

/** Where a {@link GameRun} is in its lifecycle. */
public enum RunPhase {
    /** A turn is being played. */
    PLAYING,
    /** The turn is over and its result is shown. */
    REVEAL,
    /** All rounds are done; the podium is shown. */
    FINISHED
}
