package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.SlidingPuzzleConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.SlidingPuzzleRound;
import org.springframework.stereotype.Component;

/** Strategy implementation for Sliding Puzzle game. */
@Component
public class SlidingPuzzleStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "sliding-puzzle";

    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }

    @Override
    public BaseGameConfig createDefaultConfig() {
        SlidingPuzzleConfig config = new SlidingPuzzleConfig();
        config.setRoundDuration(90);
        config.setGridSize(4); // Default 4x4
        return config;
    }

    @Override
    public BaseGameRound createRound(Game game) {
        game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);

        SlidingPuzzleConfig config = game.getTypedConfig(SlidingPuzzleConfig.class);
        int gridSize = config != null ? config.getGridSize() : 4;
        SlidingPuzzleConfig.Difficulty difficulty =
                config != null ? config.getDifficulty() : SlidingPuzzleConfig.Difficulty.MEDIUM;

        SlidingPuzzleRound round =
                new SlidingPuzzleRound(game.getCurrentRoundNumber(), gridSize, difficulty);

        // Initialize all players with the same puzzle
        for (Player player : game.getPlayers()) {
            round.initializePlayer(player.getName());
        }

        return round;
    }

    @Override
    public GameState getActiveGameState() {
        return GameState.GUESSING; // Reuse GUESSING state for puzzle solving
    }

    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof SlidingPuzzleRound round)) {
            return;
        }

        SlidingPuzzleConfig config = game.getTypedConfig(SlidingPuzzleConfig.class);
        int pointsPerTile = config != null ? config.getPointsPerCorrectTile() : 5;
        int completionBonus = config != null ? config.getCompletionBonus() : 25;
        int timeBonusPerSec = config != null ? config.getTimeBonusPerSecond() : 1;
        int roundDuration =
                game.getGameConfig() != null ? game.getGameConfig().getRoundDuration() : 90;

        for (Player player : game.getPlayers()) {
            SlidingPuzzleRound.PlayerPuzzleState state =
                    round.getPlayerStates().get(player.getName());

            if (state == null) {
                continue;
            }

            int points = 0;

            // Points for each correct tile (5 pts each)
            int correctTiles = round.countCorrectTiles(state.getPuzzle());
            points += correctTiles * pointsPerTile;

            // Completion bonus (25 pts)
            if (state.isSolved()) {
                points += completionBonus;

                // Time bonus: 1 pt per remaining second
                long elapsedSeconds = state.getSolveTime() / 1000;
                long remainingSeconds = Math.max(0, roundDuration - elapsedSeconds);
                points += (int) remainingSeconds * timeBonusPerSec;
            }

            player.setScore(player.getScore() + points);
        }
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof SlidingPuzzleRound round)) {
            return false;
        }
        return round.allPlayersSolved(game.getPlayers().size());
    }
}
