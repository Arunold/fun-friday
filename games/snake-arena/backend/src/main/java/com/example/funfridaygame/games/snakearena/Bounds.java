package com.example.funfridaygame.games.snakearena;

/**
 * The playable rectangle in cell coordinates (inclusive); every cell outside it is wall.
 *
 * @param x0 leftmost playable column
 * @param y0 topmost playable row
 * @param x1 rightmost playable column
 * @param y1 bottom playable row
 */
record Bounds(int x0, int y0, int x1, int y1) {

    /** The whole grid. */
    static Bounds of(int cols, int rows) {
        return new Bounds(0, 0, cols - 1, rows - 1);
    }

    /** Playable columns. */
    int width() {
        return x1 - x0 + 1;
    }

    /** Playable rows. */
    int height() {
        return y1 - y0 + 1;
    }

    /** Whether the position is playable. */
    boolean contains(int x, int y) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    /**
     * The walls one cell closer on every side; a dimension stops shrinking once one more step would
     * make it smaller than {@code minWidth} × {@code minHeight}.
     */
    Bounds shrink(int minWidth, int minHeight) {
        int dx = width() - 2 >= minWidth ? 1 : 0;
        int dy = height() - 2 >= minHeight ? 1 : 0;
        return new Bounds(x0 + dx, y0 + dy, x1 - dx, y1 - dy);
    }
}
