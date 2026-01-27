package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.SpeedTypingConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.SpeedTypingRound;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy implementation for Speed Typing Race game.
 */
@Component
public class SpeedTypingStrategy implements GameStrategy {
    
    private static final String GAME_TYPE_ID = "speed-typing";
    
    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }
    
    @Override
    public BaseGameConfig createDefaultConfig() {
        SpeedTypingConfig config = new SpeedTypingConfig();
        config.setRoundDuration(45);
        return config;
    }
    
    @Override
    public BaseGameRound createRound(Game game) {
        game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);
        
        SpeedTypingConfig config = game.getTypedConfig(SpeedTypingConfig.class);
        SpeedTypingConfig.Difficulty difficulty = config != null ? config.getDifficulty() : SpeedTypingConfig.Difficulty.MEDIUM;
        int minAccuracy = config != null ? config.getMinAccuracy() : 95;
        
        return new SpeedTypingRound(game.getCurrentRoundNumber(), difficulty, minAccuracy);
    }
    
    @Override
    public GameState getActiveGameState() {
        return GameState.GUESSING; // Reuse GUESSING state for typing
    }
    
    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof SpeedTypingRound round)) {
            return;
        }
        
        String targetText = round.getTargetText();
        int roundDuration = game.getGameConfig() != null ? game.getGameConfig().getRoundDuration() : 45;
        
        for (Player player : game.getPlayers()) {
            SpeedTypingRound.PlayerProgress progress = round.getPlayerProgress().get(player.getName());
            
            if (progress == null) {
                continue;
            }
            
            String typedText = progress.getTypedText();
            int points = 0;
            
            // Score each character: +2 for correct, -1 for incorrect
            int compareLength = Math.min(typedText.length(), targetText.length());
            for (int i = 0; i < compareLength; i++) {
                if (typedText.charAt(i) == targetText.charAt(i)) {
                    points += 2; // Correct character (position and case match)
                } else {
                    points -= 1; // Wrong character
                }
            }
            
            // Penalize for missing characters (if typed less than target)
            // or extra characters (if typed more than target)
            int lengthDiff = Math.abs(typedText.length() - targetText.length());
            points -= lengthDiff; // -1 for each extra/missing character
            
            // Bonus for 100% accuracy
            if (progress.getAccuracy() == 100) {
                points += 10;
            }
            
            // Time bonus: remaining seconds (only if finished)
            if (progress.isFinished() && progress.getFinishTime() > 0) {
                long elapsedSeconds = progress.getFinishTime() / 1000;
                long remainingSeconds = Math.max(0, roundDuration - elapsedSeconds);
                points += (int) remainingSeconds;
            }
            
            // Ensure minimum 0 points
            points = Math.max(0, points);
            
            player.setScore(player.getScore() + points);
        }
    }
    
    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        return round.getAnsweredCount() >= game.getPlayers().size();
    }
}
