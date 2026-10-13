package com.example.funfridaygame.games.backseatdrivers;

import java.util.Locale;

/** The "Variation" setting. */
enum Variation {
    /** The game as described: shout directions, press your control. */
    CLASSIC("Classic"),
    /** The top speed rises during each level and the brakes get weaker. */
    RUSH_HOUR("Rush hour"),
    /** The navigator can't talk: they click signals that flash on everyone's screen. */
    SILENT_NAVIGATOR("Silent navigator");

    private final String label;

    Variation(String label) {
        this.label = label;
    }

    /** The setting value, e.g. {@code silent-navigator}. */
    String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The label in the settings form. */
    String label() {
        return label;
    }

    /** The variation for a setting value. */
    static Variation fromKey(String key) {
        for (Variation variation : values()) {
            if (variation.key().equals(key)) {
                return variation;
            }
        }
        throw new IllegalArgumentException("Unknown variation " + key);
    }
}
