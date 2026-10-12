package com.example.funfridaygame.games.wordscramble;

/**
 * Points for a solve: by solve order (100, 80, 65, then 50) plus up to {@value #SPEED_BONUS} for
 * the share of the round still left, minus {@value #HINT_PENALTY_PERCENT} % per hint used. Unsolved
 * players score nothing.
 */
final class Scoring {

    /** Maximum speed bonus (solving at the very start of the round). */
    static final int SPEED_BONUS = 20;

    /** Points lost per hint, in percent of the round's points. */
    static final int HINT_PENALTY_PERCENT = 25;

    private static final int[] PLACE_POINTS = {100, 80, 65};
    private static final int LATER_PLACES = 50;

    private Scoring() {}

    /** Points for the solve order alone (rank 1 = first to solve). */
    static int placePoints(int rank) {
        return rank >= 1 && rank <= PLACE_POINTS.length ? PLACE_POINTS[rank - 1] : LATER_PLACES;
    }

    /**
     * Speed bonus: {@value #SPEED_BONUS} × the fraction of the round left when solving.
     *
     * @param solvedAt when the player solved (epoch ms)
     * @param turnStartedAt when the round started (epoch ms)
     * @param deadline when the round ends (epoch ms)
     */
    static int speedBonus(long solvedAt, long turnStartedAt, long deadline) {
        long length = Math.max(1, deadline - turnStartedAt);
        double left = Math.clamp((double) (deadline - solvedAt) / length, 0.0, 1.0);
        return (int) Math.round(SPEED_BONUS * left);
    }

    /**
     * Round points of a solver.
     *
     * @param rank solve order, starting at 1
     * @param solvedAt when the player solved (epoch ms)
     * @param turnStartedAt when the round started (epoch ms)
     * @param deadline when the round ends (epoch ms)
     * @param hintsUsed hints the player took this round
     */
    static int points(int rank, long solvedAt, long turnStartedAt, long deadline, int hintsUsed) {
        int raw = placePoints(rank) + speedBonus(solvedAt, turnStartedAt, deadline);
        int keptPercent = Math.max(0, 100 - HINT_PENALTY_PERCENT * hintsUsed);
        return (int) Math.round(raw * keptPercent / 100.0);
    }
}
