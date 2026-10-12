package com.example.funfridaygame.games.triviablitz;

import java.util.Arrays;
import java.util.Optional;

/**
 * How hard a question is. {@link #id()} is used in the question bank file, in the view and as the
 * value of the {@value TriviaBlitzGame#DIFFICULTY} setting. The declaration order (easy first) is
 * the order in which a mixed-difficulty game asks its questions.
 */
enum Difficulty {
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard");

    private final String id;
    private final String label;

    Difficulty(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Stable identifier used in the question bank, the settings and the view. */
    String id() {
        return id;
    }

    /** Display name, e.g. "Medium". */
    String label() {
        return label;
    }

    /** The difficulty with the given {@link #id()}, if there is one. */
    static Optional<Difficulty> byId(String id) {
        return Arrays.stream(values()).filter(d -> d.id.equals(id)).findFirst();
    }
}
