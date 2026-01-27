package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Word Scramble game.
 */
@Getter
@Setter
@NoArgsConstructor
public class WordScrambleConfig extends BaseGameConfig {

    private int wordLength = 7;

    @Override
    public void onRoundStart(int playerCount) {
        // No special initialization needed
    }

    @Override
    public void onRoundEnd() {
        // No cleanup needed
    }
}
