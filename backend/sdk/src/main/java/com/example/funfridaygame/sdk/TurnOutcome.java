package com.example.funfridaygame.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a turn.
 *
 * @param points points earned per player id this turn (may be negative; totals never drop below 0)
 * @param headline one line for the reveal screen, e.g. "The number was 42"
 */
public record TurnOutcome(Map<String, Integer> points, String headline) {

    public TurnOutcome {
        points = Map.copyOf(points);
        headline = headline == null ? "" : headline;
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Accumulates points per player. */
    public static final class Builder {
        private final Map<String, Integer> points = new LinkedHashMap<>();
        private String headline = "";

        private Builder() {}

        /** Adds points for a player (can be called several times). */
        public Builder add(String playerId, int value) {
            points.merge(playerId, value, Integer::sum);
            return this;
        }

        /** Sets the reveal headline. */
        public Builder headline(String text) {
            this.headline = text;
            return this;
        }

        /** Builds the outcome. */
        public TurnOutcome build() {
            return new TurnOutcome(points, headline);
        }
    }
}
