package com.example.funfridaygame.games.memorymatch;

import java.util.List;

/**
 * What a browser receives for Memory Match. Everyone (players and spectators) sees the same board:
 * a card's emoji is only included while it is face up or matched, so face-down cards can't be read
 * from the network traffic.
 *
 * @param rows board rows
 * @param cols board columns
 * @param cards every card in board order (index = row × cols + column)
 * @param activePlayerId whose move it is ({@code null} once the round is over)
 * @param moveDeadline server time (epoch ms) when the current move times out; {@code null} while a
 *     miss is shown and after the round
 * @param moveTimeMs the full time per move, for the timer ring
 * @param resolving whether a miss is being shown before the cards flip back (no flips allowed)
 * @param pairsLeft pairs nobody has found yet
 * @param pairsTotal pairs on the board
 * @param streak pairs the active player has found in a row on this move streak
 * @param players the round's order of play with this round's pairs and points
 * @param last the latest move result, for feedback and animations ({@code null} at the start of a
 *     round)
 * @param revealed whether the round is over (every card is shown face up)
 */
public record MemoryMatchView(
        int rows,
        int cols,
        List<CardView> cards,
        String activePlayerId,
        Long moveDeadline,
        long moveTimeMs,
        boolean resolving,
        int pairsLeft,
        int pairsTotal,
        int streak,
        List<PlayerProgress> players,
        MoveResult last,
        boolean revealed) {

    /**
     * One card.
     *
     * @param index position on the board
     * @param state HIDDEN, UP or MATCHED
     * @param emoji the face; only for UP and MATCHED cards
     * @param matchedBy id of the player who found the pair (MATCHED cards only)
     */
    public record CardView(int index, String state, String emoji, String matchedBy) {}

    /**
     * A player's progress in the current round.
     *
     * @param playerId player id
     * @param pairs pairs found this round
     * @param points points from pairs this round (the most-pairs bonus is added at the reveal)
     * @param mostPairs after the reveal: whether they found the most pairs (and earned the bonus)
     */
    public record PlayerProgress(String playerId, int pairs, int points, Boolean mostPairs) {}

    /**
     * The outcome of the latest move.
     *
     * @param seq increases with every result, so clients can tell a new result from a re-sent one
     * @param kind MATCH (the player goes again), MISS, TIMEOUT (no flip in time) or AWAY (the
     *     player disconnected or left during their move)
     * @param playerId who moved
     * @param cards the cards involved
     * @param points points earned (MATCH only)
     * @param streak pairs in a row including this one (MATCH only)
     */
    public record MoveResult(
            int seq, String kind, String playerId, List<Integer> cards, int points, int streak) {}
}
