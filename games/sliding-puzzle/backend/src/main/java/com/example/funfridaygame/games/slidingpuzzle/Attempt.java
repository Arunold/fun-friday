package com.example.funfridaygame.games.slidingpuzzle;

/** One player's copy of the round's puzzle: their board, move count and solve time. */
final class Attempt {

    private final Board board;
    private int moves;
    private long solvedAfterMillis = -1;

    Attempt(Board board) {
        this.board = board;
    }

    /** The player's board (their own copy). */
    Board board() {
        return board;
    }

    /** Tiles moved so far; a push that slides three tiles counts three. */
    int moves() {
        return moves;
    }

    /** Whether the player solved the puzzle. */
    boolean solved() {
        return solvedAfterMillis >= 0;
    }

    /** Milliseconds from the start of the round to the solve, {@code -1} while unsolved. */
    long solvedAfterMillis() {
        return solvedAfterMillis;
    }

    /** Percentage of the tiles that are home. */
    int progressPercent() {
        return (int) Math.round(100.0 * board.tilesInPlace() / board.tileCount());
    }

    /**
     * Slides a tile toward the gap and records the solve when the board becomes solved.
     *
     * @param elapsedMillis time since the round started
     * @return the number of tiles moved; 0 when the tile isn't in line with the gap
     */
    int slide(int tile, long elapsedMillis) {
        int moved = board.slide(tile);
        moves += moved;
        if (moved > 0 && board.isSolved()) {
            solvedAfterMillis = elapsedMillis;
        }
        return moved;
    }
}
