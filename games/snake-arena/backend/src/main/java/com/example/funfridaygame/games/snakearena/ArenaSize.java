package com.example.funfridaygame.games.snakearena;

/** The arena sizes. The grid stays fixed for a whole round, so everyone plays on the same field. */
enum ArenaSize {
    SMALL("small", 36, 24),
    MEDIUM("medium", 44, 28),
    LARGE("large", 52, 32);

    /** Setting value: pick the size by the number of players when a round starts. */
    static final String AUTO = "auto";

    private final String key;
    private final int cols;
    private final int rows;

    ArenaSize(String key, int cols, int rows) {
        this.key = key;
        this.cols = cols;
        this.rows = rows;
    }

    /** Setting value of this size. */
    String key() {
        return key;
    }

    /** Columns of the grid. */
    int cols() {
        return cols;
    }

    /** Rows of the grid. */
    int rows() {
        return rows;
    }

    /** The automatic size: up to 3 players small, up to 6 medium, otherwise large. */
    static ArenaSize forPlayers(int players) {
        if (players <= 3) {
            return SMALL;
        }
        return players <= 6 ? MEDIUM : LARGE;
    }

    /** The size for a setting value ({@value #AUTO} or a size key) and a player count. */
    static ArenaSize resolve(String setting, int players) {
        for (ArenaSize size : values()) {
            if (size.key.equals(setting)) {
                return size;
            }
        }
        return forPlayers(players);
    }
}
