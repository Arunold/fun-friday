package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuration for Sliding Puzzle game. */
@Getter
@Setter
@NoArgsConstructor
public class SlidingPuzzleConfig extends BaseGameConfig {

    public enum Difficulty {
        EASY, // 3x3 grid
        MEDIUM, // 4x4 grid
        HARD // 5x5 grid
    }

    private Difficulty difficulty = Difficulty.MEDIUM;
    private int gridSize = 4; // Default 4x4

    // Scoring configuration
    private int pointsPerCorrectTile = 5;
    private int completionBonus = 25;
    private int timeBonusPerSecond = 1;

    public SlidingPuzzleConfig(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.gridSize = getGridSizeForDifficulty(difficulty);
        this.roundDuration = getDurationForDifficulty(difficulty);
    }

    private int getGridSizeForDifficulty(Difficulty diff) {
        return switch (diff) {
            case EASY -> 3;
            case MEDIUM -> 4;
            case HARD -> 5;
        };
    }

    private int getDurationForDifficulty(Difficulty diff) {
        return switch (diff) {
            case EASY -> 60;
            case MEDIUM -> 90;
            case HARD -> 120;
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
