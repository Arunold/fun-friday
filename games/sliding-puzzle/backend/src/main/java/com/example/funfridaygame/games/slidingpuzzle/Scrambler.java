package com.example.funfridaygame.games.slidingpuzzle;

import java.util.random.RandomGenerator;

/**
 * Creates the scramble of a round: a long random walk of legal moves from the solved board, so the
 * puzzle is always solvable (a random permutation is unsolvable half of the time).
 *
 * <p>Walks that end too close to the solution are continued until the board is hard enough
 * (IMP-28): its Manhattan distance reaches {@link #minimumDistance(int)} — 85% of what a uniformly
 * random board averages — and at least 70% of the tiles are out of place. The solved board can
 * therefore never be dealt.
 */
final class Scrambler {

    /** Random moves per cell before the first difficulty check. */
    private static final int MOVES_PER_CELL = 40;

    /** Safety net for the (astronomically unlikely) case that no walk ever gets hard enough. */
    private static final int MAX_EXTENSIONS = 10_000;

    private Scrambler() {}

    /**
     * Scrambles a solved board of the given size.
     *
     * @throws IllegalStateException if no hard-enough board turns up (practically impossible for
     *     the supported sizes 3 to 5)
     */
    static Board scramble(int size, RandomGenerator random) {
        Board board = Board.solved(size);
        int cameFrom = -1;
        int moves = size * size * MOVES_PER_CELL;
        for (int extension = 0; extension <= MAX_EXTENSIONS; extension++) {
            for (int i = 0; i < moves; i++) {
                cameFrom = step(board, cameFrom, random);
            }
            if (isHardEnough(board)) {
                return board;
            }
            moves = size * size;
        }
        throw new IllegalStateException("Could not scramble a " + size + "×" + size + " board");
    }

    /**
     * The Manhattan distance a scramble needs at least: 85% of the average distance of a uniformly
     * random board, rounded (12 for 3×3, 32 for 4×4, 65 for 5×5).
     */
    static int minimumDistance(int size) {
        int tiles = size * size - 1;
        // A tile's expected row (and column) distance to a random cell is (n² - 1) / (3n).
        double averageDistance = tiles * 2.0 * tiles / (3.0 * size);
        return (int) Math.round(0.85 * averageDistance);
    }

    /** Whether a board is far enough from the solution to be dealt. */
    static boolean isHardEnough(Board board) {
        return board.manhattanDistance() >= minimumDistance(board.size())
                && board.misplacedTiles() * 10 >= board.tileCount() * 7;
    }

    /**
     * One random move that doesn't undo the previous one.
     *
     * @param cameFrom the cell the gap was in before the previous move ({@code -1} at the start)
     * @return the cell the gap was in before this move
     */
    private static int step(Board board, int cameFrom, RandomGenerator random) {
        int[] neighbours = board.gapNeighbours();
        int[] options = new int[neighbours.length];
        int count = 0;
        for (int cell : neighbours) {
            if (cell != cameFrom) {
                options[count++] = cell;
            }
        }
        int from = board.gapIndex();
        board.moveIntoGap(options[random.nextInt(count)]);
        return from;
    }
}
