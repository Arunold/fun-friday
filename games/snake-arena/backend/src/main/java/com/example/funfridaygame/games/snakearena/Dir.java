package com.example.funfridaygame.games.snakearena;

/** A heading on the grid; {@code y} grows downwards. */
enum Dir {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    private final int dx;
    private final int dy;

    Dir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** Column change of one step. */
    int dx() {
        return dx;
    }

    /** Row change of one step. */
    int dy() {
        return dy;
    }

    /** The reverse heading. */
    Dir opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
