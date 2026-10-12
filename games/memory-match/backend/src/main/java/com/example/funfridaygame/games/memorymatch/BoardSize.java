package com.example.funfridaygame.games.memorymatch;

import java.util.Arrays;

/**
 * The board layouts the host can choose. Boards are wider than tall (rows × columns) because the
 * stage is a landscape area on a desktop screen.
 */
enum BoardSize {
    /** 4 × 4 cards, 8 pairs. */
    SMALL("4x4", "4×4", 4, 4),
    /** 4 × 5 cards, 10 pairs. */
    MEDIUM("4x5", "4×5", 4, 5),
    /** 4 × 6 cards, 12 pairs. */
    LARGE("4x6", "4×6", 4, 6),
    /** 6 × 6 cards, 18 pairs. */
    HUGE("6x6", "6×6", 6, 6);

    private final String key;
    private final String label;
    private final int rows;
    private final int cols;

    BoardSize(String key, String label, int rows, int cols) {
        this.key = key;
        this.label = label;
        this.rows = rows;
        this.cols = cols;
    }

    /** Setting value, e.g. {@code "4x6"}. */
    String key() {
        return key;
    }

    /** Option label shown to the host, e.g. {@code "4×6"}. */
    String label() {
        return label;
    }

    int rows() {
        return rows;
    }

    int cols() {
        return cols;
    }

    /** Number of cards on the board. */
    int cards() {
        return rows * cols;
    }

    /** Number of pairs on the board. */
    int pairs() {
        return cards() / 2;
    }

    /**
     * The layout for a setting value.
     *
     * @throws IllegalArgumentException for unknown values (the platform validates settings first)
     */
    static BoardSize fromKey(String key) {
        return Arrays.stream(values())
                .filter(size -> size.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown board " + key));
    }
}
