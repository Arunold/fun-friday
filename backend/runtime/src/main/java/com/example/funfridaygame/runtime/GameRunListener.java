package com.example.funfridaygame.runtime;

/** Receives what a {@link GameRun} wants to tell the outside world. */
public interface GameRunListener {

    /**
     * The state changed and clients need a fresh snapshot.
     *
     * @param urgent {@code true} for phase changes and timer events that must go out at once;
     *     {@code false} for player actions, which may be coalesced
     */
    void changed(boolean urgent);

    /** A public event for everyone in the room (see {@code GameContext#stream}). */
    void stream(String type, Object payload);

    /** A private event for one player (see {@code GameContext#sendTo}). */
    void sendTo(String playerId, String type, Object payload);

    /** The game reached the podium. */
    void finished(GameResult result);
}
