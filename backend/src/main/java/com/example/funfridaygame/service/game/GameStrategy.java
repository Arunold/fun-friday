package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.round.BaseGameRound;

/**
 * Strategy interface for game-specific logic.
 * Each game type implements this to handle its unique mechanics.
 */
public interface GameStrategy {
    
    /**
     * Get the game type ID this strategy handles
     */
    String getGameTypeId();
    
    /**
     * Create the default configuration for this game type
     */
    BaseGameConfig createDefaultConfig();
    
    /**
     * Create a new round for this game type
     */
    BaseGameRound createRound(Game game);
    
    /**
     * Get the game state when a round is active
     */
    GameState getActiveGameState();
    
    /**
     * Award points to players based on round results
     */
    void awardPoints(Game game, BaseGameRound round);
    
    /**
     * Check if all players have answered (for early round end)
     */
    boolean allPlayersAnswered(Game game, BaseGameRound round);
    
    /**
     * Check if round should end early (e.g., correct answer in word scramble)
     */
    default boolean shouldEndRoundEarly(Game game, BaseGameRound round) {
        return allPlayersAnswered(game, round);
    }
}
