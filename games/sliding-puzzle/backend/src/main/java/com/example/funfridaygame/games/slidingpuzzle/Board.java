package com.example.funfridaygame.games.slidingpuzzle;

import java.util.Arrays;
import java.util.List;

/**
 * A square sliding-puzzle board. Cells are stored in reading order; tiles are numbered {@code 1 …
 * n²-1} and {@link #GAP} marks the empty cell. The solved board has every tile at index {@code tile
 * - 1} and the gap in the bottom-right corner.
 *
 * <p>Mutable: {@link #slide(int)} and {@link #moveIntoGap(int)} change the board in place; use
 * {@link #copy()} to give every player their own board.
 */
final class Board {

    /** The value of the empty cell. */
    static final int GAP = 0;

    private final int size;
    private final int[] cells;
    private int gap;

    private Board(int size, int[] cells) {
        this.size = size;
        this.cells = cells;
        this.gap = indexOf(GAP);
    }

    /** The solved board of the given size. */
    static Board solved(int size) {
        if (size < 2) {
            throw new IllegalArgumentException("A board needs at least 2×2 cells");
        }
        int[] cells = new int[size * size];
        for (int i = 0; i < cells.length - 1; i++) {
            cells[i] = i + 1;
        }
        return new Board(size, cells);
    }

    /**
     * A board from its cells in reading order.
     *
     * @throws IllegalArgumentException unless {@code cells} holds every number {@code 0 … size²-1}
     *     exactly once
     */
    static Board of(int size, int... cells) {
        if (size < 2 || cells.length != size * size) {
            throw new IllegalArgumentException("Expected " + size * size + " cells");
        }
        boolean[] seen = new boolean[cells.length];
        for (int value : cells) {
            if (value < 0 || value >= cells.length || seen[value]) {
                throw new IllegalArgumentException("Not a permutation: " + Arrays.toString(cells));
            }
            seen[value] = true;
        }
        return new Board(size, cells.clone());
    }

    /** An independent copy. */
    Board copy() {
        return new Board(size, cells.clone());
    }

    /** Cells per row (and per column). */
    int size() {
        return size;
    }

    /** Number of tiles (cells without the gap). */
    int tileCount() {
        return cells.length - 1;
    }

    /** Index of the empty cell. */
    int gapIndex() {
        return gap;
    }

    /** The tile at a cell, {@link #GAP} for the empty cell. */
    int tileAt(int index) {
        return cells[index];
    }

    /** The cells in reading order (immutable), as sent to browsers. */
    List<Integer> cells() {
        return Arrays.stream(cells).boxed().toList();
    }

    /** Whether every tile is home. */
    boolean isSolved() {
        return tilesInPlace() == tileCount();
    }

    /** Tiles at their home cell (the gap doesn't count). */
    int tilesInPlace() {
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == i + 1) {
                count++;
            }
        }
        return count;
    }

    /** Tiles away from their home cell. */
    int misplacedTiles() {
        return tileCount() - tilesInPlace();
    }

    /**
     * Sum of every tile's row and column distance to its home cell. Each single-tile move changes
     * it by exactly one, so it is a lower bound for the moves a solution needs.
     */
    int manhattanDistance() {
        int distance = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != GAP) {
                int home = cells[i] - 1;
                distance += Math.abs(i / size - home / size) + Math.abs(i % size - home % size);
            }
        }
        return distance;
    }

    /**
     * How many tiles slide when {@code tile} is pushed toward the gap: its distance to the gap when
     * it shares the gap's row or column, otherwise 0.
     */
    int slideLength(int tile) {
        if (tile <= GAP || tile >= cells.length) {
            return 0;
        }
        int index = indexOf(tile);
        if (index / size == gap / size) {
            return Math.abs(index % size - gap % size);
        }
        if (index % size == gap % size) {
            return Math.abs(index / size - gap / size);
        }
        return 0;
    }

    /**
     * Slides {@code tile} one cell toward the gap, together with the tiles between them — like
     * pushing a row of tiles in a real puzzle.
     *
     * @return the number of tiles that moved; 0 (and no change) when the tile isn't in line with
     *     the gap
     */
    int slide(int tile) {
        int length = slideLength(tile);
        if (length == 0) {
            return 0;
        }
        int target = indexOf(tile);
        int step =
                target / size == gap / size
                        ? Integer.signum(target - gap)
                        : Integer.signum(target - gap) * size;
        while (gap != target) {
            moveIntoGap(gap + step);
        }
        return length;
    }

    /**
     * Moves the tile at {@code index}, which must be next to the gap, into the gap.
     *
     * @throws IllegalArgumentException when the cell isn't next to the gap
     */
    void moveIntoGap(int index) {
        if (!isNextToGap(index)) {
            throw new IllegalArgumentException("Cell " + index + " is not next to the gap");
        }
        cells[gap] = cells[index];
        cells[index] = GAP;
        gap = index;
    }

    /** The cells next to the gap (2 to 4 of them). */
    int[] gapNeighbours() {
        int row = gap / size;
        int col = gap % size;
        int[] candidates = new int[4];
        int count = 0;
        if (row > 0) {
            candidates[count++] = gap - size;
        }
        if (row < size - 1) {
            candidates[count++] = gap + size;
        }
        if (col > 0) {
            candidates[count++] = gap - 1;
        }
        if (col < size - 1) {
            candidates[count++] = gap + 1;
        }
        return Arrays.copyOf(candidates, count);
    }

    private boolean isNextToGap(int index) {
        if (index < 0 || index >= cells.length) {
            return false;
        }
        int rows = Math.abs(index / size - gap / size);
        int cols = Math.abs(index % size - gap % size);
        return rows + cols == 1;
    }

    private int indexOf(int value) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == value) {
                return i;
            }
        }
        throw new IllegalArgumentException("No cell holds " + value);
    }

    @Override
    public String toString() {
        return size + "×" + size + " " + Arrays.toString(cells);
    }
}
