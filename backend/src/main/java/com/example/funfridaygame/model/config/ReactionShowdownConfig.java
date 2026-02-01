package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Reaction Showdown game. Players test their reaction time - tap when the signal
 * appears!
 */
@Getter
@Setter
@NoArgsConstructor
public class ReactionShowdownConfig extends BaseGameConfig {

    // Minimum delay before signal (milliseconds)
    private int minDelay = 2000;

    // Maximum delay before signal (milliseconds)
    private int maxDelay = 5000;

    // Whether to include fake-out rounds
    private boolean includeFakeOuts = true;

    // Chance of a fake-out round (0-100)
    private int fakeOutChance = 20;

    @Override
    public void onRoundStart(int playerCount) {
        // No special initialization needed
    }

    @Override
    public void onRoundEnd() {
        // No cleanup needed
    }
}
