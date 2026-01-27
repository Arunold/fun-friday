package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Number Guess game.
 * Currently minimal - just uses base config.
 */
@Getter
@Setter
@NoArgsConstructor
public class NumberGuessConfig extends BaseGameConfig {

    private int minRange = 1;
    private int maxRange = 100;

    @Override
    public void onRoundStart(int playerCount) {
        // No special initialization needed
    }

    @Override
    public void onRoundEnd() {
        // No cleanup needed
    }
}
