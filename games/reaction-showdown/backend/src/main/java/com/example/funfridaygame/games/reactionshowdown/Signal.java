package com.example.funfridaygame.games.reactionshowdown;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The signal being played: its secret plan, what is on screen right now and every player's
 * reaction. One instance per signal; a new signal gets a new instance and a new id, so a late tap
 * or timer can never be confused with the next signal (BUG-25).
 */
final class Signal {

    private final int id;
    private final int index;
    private final SignalPlan plan;
    private final Map<String, Reaction> reactions = new LinkedHashMap<>();
    private SignalState state = SignalState.WAITING;
    private boolean fakeShown;
    private long goAt = -1;
    private ReactionShowdownView.SignalResult result;

    /**
     * Creates a signal in the {@link SignalState#WAITING} state.
     *
     * @param id unique id within the game (sent to browsers; taps must quote it)
     * @param index 0-based position within the round
     * @param plan the secret schedule
     */
    Signal(int id, int index, SignalPlan plan) {
        this.id = id;
        this.index = index;
        this.plan = plan;
    }

    int id() {
        return id;
    }

    int index() {
        return index;
    }

    SignalPlan plan() {
        return plan;
    }

    SignalState state() {
        return state;
    }

    /** Server time when GO appeared, or -1 before. */
    long goAt() {
        return goAt;
    }

    /** Whether a decoy was actually shown (a planned one is skipped if everyone jumped early). */
    boolean fakeShown() {
        return fakeShown;
    }

    /** The decoy on screen right now, or {@code null} — never a decoy that is still to come. */
    FakeOut visibleFake() {
        return state == SignalState.FAKE ? plan.fake() : null;
    }

    /** Whether a planned decoy has not been shown yet (debug state only, never in views). */
    boolean fakeComing() {
        return state == SignalState.WAITING && plan.hasFake();
    }

    /** The score of this signal once it is over, otherwise {@code null}. */
    ReactionShowdownView.SignalResult result() {
        return result;
    }

    /** Shows the planned decoy. */
    void showFake() {
        state = SignalState.FAKE;
        fakeShown = true;
    }

    /** Shows GO and remembers when (the reference for the server-side plausibility check). */
    void go(long now) {
        state = SignalState.GO;
        goAt = now;
    }

    /** Ends the signal with its score. */
    void finish(ReactionShowdownView.SignalResult score) {
        state = SignalState.RESULT;
        result = score;
    }

    /** The player's reaction to this signal, or {@code null} if they haven't tapped. */
    Reaction reaction(String playerId) {
        return reactions.get(playerId);
    }

    /** Whether the player tapped (validly or not). */
    boolean hasReacted(String playerId) {
        return reactions.containsKey(playerId);
    }

    /** Records a player's (first and only) tap. */
    void record(String playerId, Reaction reaction) {
        reactions.putIfAbsent(playerId, reaction);
    }

    /** Every reaction so far, in tap order. */
    Map<String, Reaction> reactions() {
        return Collections.unmodifiableMap(reactions);
    }
}
