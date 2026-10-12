package com.example.funfridaygame.games.speedtyping;

import java.time.Duration;
import java.util.Objects;

/**
 * The host's choices for one game.
 *
 * @param length how long the texts are
 * @param roundSeconds round timer in seconds; {@code 0} means automatic by text length
 * @param minAccuracy minimum accuracy in percent; finishers below it get half points
 */
record RaceConfig(TextLength length, int roundSeconds, int minAccuracy) {

    RaceConfig {
        Objects.requireNonNull(length, "length");
        if (roundSeconds < 0) {
            throw new IllegalArgumentException("roundSeconds must not be negative");
        }
        if (minAccuracy < 0 || minAccuracy > 100) {
            throw new IllegalArgumentException("minAccuracy must be a percentage");
        }
    }

    /** The round timer: the host's choice, or the automatic one for the text length. */
    Duration roundDuration() {
        return Duration.ofSeconds(roundSeconds > 0 ? roundSeconds : length.autoSeconds());
    }
}
