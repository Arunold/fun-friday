package com.example.funfridaygame.sdk;

import java.util.List;

/**
 * The rules of one game run. Created by {@link GameDefinition#createLogic(GameSettings)} when the
 * host starts a game and discarded when the podium is shown.
 *
 * <p><b>Lifecycle.</b> A game has {@link GameContext#totalRounds()} rounds; each round has {@link
 * #turnsInRound(GameContext)} turns (1 for most games; Pictionary has one per drawer). For every
 * turn the platform calls {@link #startTurn}, then forwards player actions ({@link #onAction}) and
 * timers ({@link #onTimer}) until the deadline passes or {@link #isTurnComplete} returns true, then
 * calls {@link #endTurn}, shows the reveal screen and moves on.
 *
 * <p><b>Threading.</b> All methods are called one at a time per room; no locking is needed.
 *
 * <p><b>Views.</b> Clients never see the logic object. They receive whatever {@link #view} returns
 * for them, serialized as JSON, so hide secrets (answers, other players' guesses) there.
 */
public interface GameLogic {

    /**
     * Number of turns in the round that is about to start ({@link GameContext#round()} is already
     * the new round). Called once per round, before its first turn.
     */
    default int turnsInRound(GameContext ctx) {
        return 1;
    }

    /**
     * Whether the upcoming turn should be skipped without a reveal (e.g. its drawer left). Called
     * before {@link #startTurn}.
     */
    default boolean skipTurn(GameContext ctx) {
        return false;
    }

    /**
     * Sets up a turn. The deadline defaults to the {@value GameSettings#ROUND_DURATION} setting (or
     * 60 s); call {@link GameContext#setDeadline} to change it.
     */
    void startTurn(GameContext ctx);

    /**
     * Handles an action sent by a player during the turn. Throw {@link InvalidActionException} to
     * reject it; the sender gets the message and nothing changes.
     */
    void onAction(GameContext ctx, PlayerInfo player, Action action);

    /** Called when a timer registered with {@link GameContext#schedule} fires. */
    default void onTimer(GameContext ctx, String key) {}

    /**
     * Whether the turn can end before its deadline, e.g. everyone has answered. Checked after every
     * action, timer and roster change. Consider only {@link GameContext#activePlayers()}.
     */
    boolean isTurnComplete(GameContext ctx);

    /** Ends the turn (deadline, completion or {@link GameContext#endTurn()}) and scores it. */
    TurnOutcome endTurn(GameContext ctx);

    /**
     * What a viewer sees during a turn and its reveal. Called after every change, once per player
     * and once for spectators. Return a record or map; it is sent as JSON.
     */
    Object view(GameContext ctx, Viewer viewer);

    /** A player left the game for good (left, was kicked, or stayed disconnected too long). */
    default void onPlayerLeft(GameContext ctx, PlayerInfo player) {}

    /** Fun titles for the podium, e.g. "Fastest fingers". Called once when the game ends. */
    default List<Award> awards(GameContext ctx) {
        return List.of();
    }

    /**
     * Internal state for automated end-to-end tests (only exposed when the {@code e2e} profile is
     * active). Return e.g. the secret answer so tests can play deterministically.
     */
    default Object debugState(GameContext ctx) {
        return null;
    }
}
