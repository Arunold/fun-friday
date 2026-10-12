package com.example.funfridaygame.games.reactionshowdown;

import java.util.List;

/**
 * What a browser receives for Reaction Showdown.
 *
 * <p>Only the present is visible: the state on screen, the current signal's id and the results of
 * signals that are already over. Upcoming waits and fake-outs never appear (BUG-08, BUG-31) — a
 * fake-out's variant is sent only while it is on screen.
 *
 * @param signal 1-based number of the current signal within the round
 * @param signals signals per round
 * @param signalId unique id of the current signal; taps quote it and the client keys its GO
 *     timestamp by it
 * @param state WAITING, FAKE, GO or RESULT
 * @param fake the decoy on screen (HOLD, STEADY or FREEZE), only while {@code state} is FAKE
 * @param fakeouts whether fake-outs are enabled in this game
 * @param revealed whether the round is over (reveal screen)
 * @param me the viewer's reaction to the current signal ({@code null} for spectators)
 * @param players every player's status for the current signal
 * @param results the finished signals of this round, oldest first
 * @param table the round table, only once revealed
 */
public record ReactionShowdownView(
        int signal,
        int signals,
        int signalId,
        String state,
        String fake,
        boolean fakeouts,
        boolean revealed,
        MyStatus me,
        List<PlayerStatus> players,
        List<SignalResult> results,
        List<TableRow> table) {

    /**
     * The viewer's own state.
     *
     * @param tapped whether they tapped this signal validly
     * @param ms their accepted reaction time (valid taps only)
     * @param falseStart whether they jumped the gun on this signal
     * @param fake whether that false start was on a fake-out
     * @param roundPoints their points from the finished signals of this round
     */
    public record MyStatus(
            boolean tapped, Integer ms, boolean falseStart, boolean fake, int roundPoints) {}

    /**
     * A player's status for the current signal (times are revealed with the signal's result).
     *
     * @param playerId player id
     * @param tapped whether they tapped validly
     * @param falseStart whether they jumped the gun
     */
    public record PlayerStatus(String playerId, boolean tapped, boolean falseStart) {}

    /**
     * The outcome of one finished signal.
     *
     * @param signalId the signal's unique id
     * @param signal its 1-based number within the round
     * @param fakeOut whether a decoy flashed before its GO
     * @param fastestPlayerId the fastest valid tapper ({@code null} if nobody tapped validly)
     * @param fastestMs the fastest reaction time
     * @param entries one entry per player: valid taps by rank, then misses, then false starts
     */
    public record SignalResult(
            int signalId,
            int signal,
            boolean fakeOut,
            String fastestPlayerId,
            Integer fastestMs,
            List<Entry> entries) {}

    /**
     * One player's outcome on one signal.
     *
     * @param playerId player id
     * @param ms reaction time (valid taps only)
     * @param rank 1-based rank among valid taps, ties share a rank (valid taps only)
     * @param falseStart whether they jumped the gun
     * @param fake whether that false start was on a fake-out
     * @param points points for this signal (negative for a false start)
     */
    public record Entry(
            String playerId,
            Integer ms,
            Integer rank,
            boolean falseStart,
            boolean fake,
            int points) {}

    /**
     * One line of the round table on the reveal screen.
     *
     * @param playerId player id
     * @param rank 1-based rank by points (ties share a rank)
     * @param bestMs fastest valid reaction this round
     * @param avgMs average valid reaction this round (rounded)
     * @param points points this round
     * @param falseStarts false starts this round
     */
    public record TableRow(
            String playerId,
            int rank,
            Integer bestMs,
            Integer avgMs,
            int points,
            int falseStarts) {}
}
