package com.example.funfridaygame.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * Timing rules of the platform.
 *
 * @param revealDuration how long the reveal screen stays after each turn
 * @param defaultTurnDuration turn length for games without a round-duration setting
 */
public record GameRunConfig(Duration revealDuration, Duration defaultTurnDuration) {

    /** Production defaults: 5 s reveal, 60 s turns. */
    public static final GameRunConfig DEFAULT =
            new GameRunConfig(Duration.ofSeconds(5), Duration.ofSeconds(60));

    public GameRunConfig {
        Objects.requireNonNull(revealDuration, "revealDuration");
        Objects.requireNonNull(defaultTurnDuration, "defaultTurnDuration");
    }
}
