package com.example.funfridaygame.games.speedtyping;

import java.util.Locale;

/**
 * How long the texts of a game are. Also decides the automatic round timer, so long passages get
 * more time than single sentences (BUG-05).
 */
enum TextLength {
    /** One sentence. */
    SHORT("Short", 40, 70, 45),
    /** One or two sentences. */
    MEDIUM("Medium", 80, 140, 75),
    /** A short passage. */
    LONG("Long", 150, 260, 120);

    private final String label;
    private final int minChars;
    private final int maxChars;
    private final int autoSeconds;

    TextLength(String label, int minChars, int maxChars, int autoSeconds) {
        this.label = label;
        this.minChars = minChars;
        this.maxChars = maxChars;
        this.autoSeconds = autoSeconds;
    }

    /** Display name of the setting option. */
    String label() {
        return label;
    }

    /** Shortest allowed text of this length, in characters. */
    int minChars() {
        return minChars;
    }

    /** Longest allowed text of this length, in characters. */
    int maxChars() {
        return maxChars;
    }

    /** Round timer used when the host leaves the timer on "Auto". */
    int autoSeconds() {
        return autoSeconds;
    }

    /** Resource with the curated texts, relative to this package. */
    String resource() {
        return "texts/" + name().toLowerCase(Locale.ROOT) + ".txt";
    }
}
