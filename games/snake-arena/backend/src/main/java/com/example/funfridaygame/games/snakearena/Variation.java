package com.example.funfridaygame.games.snakearena;

/** Game modes (the {@code variation} setting). */
enum Variation {
    /** Crash, respawn after 3 s, play until the timer runs out. */
    CLASSIC("classic", "Classic"),
    /** Classic, but the walls close in during the last 30 seconds. */
    SHRINKING("shrinking", "Shrinking arena"),
    /** No respawns; the round ends when one snake is left, the survivor earns a bonus. */
    LAST_STANDING("last-standing", "Last snake standing");

    private final String key;
    private final String label;

    Variation(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Setting value. */
    String key() {
        return key;
    }

    /** Display name. */
    String label() {
        return label;
    }

    /** Whether crashed snakes come back. */
    boolean respawns() {
        return this != LAST_STANDING;
    }

    /** The variation for a setting value (classic for unknown values). */
    static Variation fromKey(String key) {
        for (Variation variation : values()) {
            if (variation.key.equals(key)) {
                return variation;
            }
        }
        return CLASSIC;
    }
}
