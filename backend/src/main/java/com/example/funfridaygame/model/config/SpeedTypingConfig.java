package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration for Speed Typing Race game.
 */
@Getter
@Setter
@NoArgsConstructor
public class SpeedTypingConfig extends BaseGameConfig {
    
    public enum Difficulty {
        EASY, MEDIUM, HARD
    }
    
    private Difficulty difficulty = Difficulty.MEDIUM;
    private int minAccuracy = 95; // Percentage required to "finish"
    
    public SpeedTypingConfig(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.roundDuration = getDurationForDifficulty(difficulty);
    }
    
    private int getDurationForDifficulty(Difficulty diff) {
        return switch (diff) {
            case EASY -> 30;
            case MEDIUM -> 45;
            case HARD -> 60;
        };
    }
    
    @Override
    public void onRoundStart(int playerCount) {
        // No special initialization needed
    }
    
    @Override
    public void onRoundEnd() {
        // No cleanup needed
    }
}
