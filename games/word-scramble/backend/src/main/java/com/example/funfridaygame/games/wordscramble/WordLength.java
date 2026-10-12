package com.example.funfridaygame.games.wordscramble;

import java.util.random.RandomGenerator;

/**
 * The "word length" setting: a fixed number of letters, or {@value #MIXED} — a length from {@value
 * #MIXED_MIN} to {@value #MIXED_MAX} drawn anew every round.
 *
 * @param fixed the length of every word, or 0 for mixed
 */
record WordLength(int fixed) {

    /** Setting value for mixed lengths. */
    static final String MIXED = "mixed";

    /** Shortest word in mixed mode. */
    static final int MIXED_MIN = 5;

    /** Longest word in mixed mode. */
    static final int MIXED_MAX = 7;

    /**
     * Parses a validated setting value ({@code "4"} … {@code "8"} or {@value #MIXED}).
     *
     * @throws IllegalArgumentException for anything else
     */
    static WordLength parse(String value) {
        if (MIXED.equals(value)) {
            return new WordLength(0);
        }
        int length = Integer.parseInt(value);
        if (length < WordBank.MIN_LENGTH || length > WordBank.MAX_LENGTH) {
            throw new IllegalArgumentException("Unsupported word length " + value);
        }
        return new WordLength(length);
    }

    /** The length of the next round's word. */
    int next(RandomGenerator random) {
        return fixed > 0 ? fixed : MIXED_MIN + random.nextInt(MIXED_MAX - MIXED_MIN + 1);
    }
}
