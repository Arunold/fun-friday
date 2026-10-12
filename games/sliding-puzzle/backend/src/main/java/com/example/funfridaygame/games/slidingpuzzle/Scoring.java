package com.example.funfridaygame.games.slidingpuzzle;

import java.util.Locale;

/**
 * Points of a Sliding Puzzle round.
 *
 * <ul>
 *   <li>Solvers by finishing order: 100, 80, 65, 55, then 50 for everyone after.
 *   <li>Solvers also get an efficiency bonus of up to {@value #EFFICIENCY_MAX}: the full bonus for
 *       solving in at most twice the scramble's Manhattan distance, half of it for twice as many
 *       moves, and so on.
 *   <li>Players who didn't solve but moved earn up to {@value #PROGRESS_MAX} for the share of tiles
 *       in place — always less than any solver.
 * </ul>
 */
final class Scoring {

    /** Bonus for the most efficient solves. */
    static final int EFFICIENCY_MAX = 30;

    /** Points for an unsolved board with every tile in place (never reached in practice). */
    static final int PROGRESS_MAX = 40;

    private static final int[] PLACE_POINTS = {100, 80, 65, 55, 50};

    private Scoring() {}

    /** Points for finishing at {@code rank} (1 = first). */
    static int placePoints(int rank) {
        return PLACE_POINTS[Math.clamp(rank, 1, PLACE_POINTS.length) - 1];
    }

    /**
     * Efficiency bonus: {@code 30 × min(1, 2 × scrambleDistance / moves)}, rounded.
     *
     * @param scrambleDistance Manhattan distance of the round's scramble (a lower bound for moves)
     * @param moves the solver's moves
     */
    static int efficiencyBonus(int scrambleDistance, int moves) {
        if (moves <= 0) {
            return 0;
        }
        double ratio = Math.min(1.0, 2.0 * scrambleDistance / moves);
        return (int) Math.round(EFFICIENCY_MAX * ratio);
    }

    /** Points for an unsolved board: {@code 40 × tiles in place / tiles}, rounded. */
    static int progressPoints(int tilesInPlace, int tiles) {
        return (int) Math.round(PROGRESS_MAX * (double) tilesInPlace / tiles);
    }

    /**
     * Points of one attempt.
     *
     * @param rank finishing position of a solver, ignored for unsolved attempts
     * @param scrambleDistance Manhattan distance of the round's scramble
     * @return the points; 0 for players who never moved
     */
    static int points(Attempt attempt, int rank, int scrambleDistance) {
        if (attempt.solved()) {
            return placePoints(rank) + efficiencyBonus(scrambleDistance, attempt.moves());
        }
        if (attempt.moves() == 0) {
            return 0;
        }
        Board board = attempt.board();
        return progressPoints(board.tilesInPlace(), board.tileCount());
    }

    /** A solve time for people: {@code 42.3 s} under a minute, {@code 2:05} above. */
    static String formatTime(long millis) {
        if (millis < 60_000) {
            return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
        }
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
