package com.example.funfridaygame.sdk;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** What the platform offers a {@link GameLogic}: game progress, players, time and messaging. */
public interface GameContext {

    /** Code of the room the game runs in. */
    String roomCode();

    /** Validated settings of this game run. */
    GameSettings settings();

    /** Current round, starting at 1. */
    int round();

    /** Rounds in this game. */
    int totalRounds();

    /** Current turn within the round, starting at 1. */
    int turn();

    /** Turns in the current round. */
    int turnsInRound();

    /** Everyone playing this game, in join order (includes temporarily disconnected players). */
    List<PlayerInfo> players();

    /** Players that are connected right now. Use these for "has everyone answered?" checks. */
    List<PlayerInfo> activePlayers();

    /** A player of this game by id. */
    Optional<PlayerInfo> player(String playerId);

    /** Whether the player is part of the game and connected. */
    boolean isActive(String playerId);

    /** Total score per player id so far (read-only). */
    Map<String, Integer> scores();

    /** Random source of this game (seeded in tests). */
    RandomGenerator random();

    /** Current time in epoch milliseconds (use this instead of the system clock). */
    long now();

    /** When the current turn started (epoch milliseconds). */
    long turnStartedAt();

    /** When the current turn ends (epoch milliseconds). */
    long deadline();

    /** Milliseconds until the deadline, never negative. */
    default long remainingMillis() {
        return Math.max(0, deadline() - now());
    }

    /** Moves the turn deadline to {@code fromNow} after the current time. */
    void setDeadline(Duration fromNow);

    /**
     * Calls {@link GameLogic#onTimer} with {@code key} after {@code delay}. Timers belong to the
     * current turn and are cancelled when it ends. Scheduling an existing key replaces it.
     */
    void schedule(String key, Duration delay);

    /** Cancels a timer registered with {@link #schedule}. */
    void cancel(String key);

    /** Ends the turn as soon as the current callback returns. */
    void endTurn();

    /**
     * Tells the platform that the current action changed nothing the views show, so no state update
     * is sent (e.g. a drawing stroke that was already relayed with {@link #stream}). Only
     * meaningful inside {@link GameLogic#onAction}.
     */
    void suppressUpdate();

    /**
     * Sends a public event to everyone in the room immediately, outside the normal state updates
     * (for high-frequency data such as drawing strokes). Never put secrets here.
     */
    void stream(String type, Object payload);

    /** Sends a private event to one player, e.g. "you're close!". */
    void sendTo(String playerId, String type, Object payload);
}
