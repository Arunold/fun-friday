package com.example.funfridaygame.games.triviablitz;

import java.util.Arrays;
import java.util.Optional;

/**
 * The question categories. {@link #id()} is used in the question bank file and as the value of the
 * {@value TriviaBlitzGame#CATEGORY} setting.
 */
enum Category {
    GENERAL("general", "General knowledge", "🧠"),
    SCIENCE("science", "Science", "🔬"),
    GEOGRAPHY("geography", "Geography", "🌍"),
    HISTORY("history", "History", "🏛️"),
    TECHNOLOGY("technology", "Technology", "💻"),
    FOOD("food", "Food & drink", "🍕"),
    SPORTS("sports", "Sports", "⚽"),
    ENTERTAINMENT("entertainment", "Movies & music", "🎬"),
    NATURE("nature", "Nature & animals", "🐾"),
    LANGUAGE("language", "Language", "🔤");

    private final String id;
    private final String label;
    private final String icon;

    Category(String id, String label, String icon) {
        this.id = id;
        this.label = label;
        this.icon = icon;
    }

    /** Stable identifier used in the question bank and the settings. */
    String id() {
        return id;
    }

    /** Display name, e.g. "Food & drink". */
    String label() {
        return label;
    }

    /** An emoji shown next to the label. */
    String icon() {
        return icon;
    }

    /** The category with the given {@link #id()}, if there is one. */
    static Optional<Category> byId(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }
}
