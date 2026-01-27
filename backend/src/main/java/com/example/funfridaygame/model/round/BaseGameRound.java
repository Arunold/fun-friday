package com.example.funfridaygame.model.round;

import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all game rounds. Each game type extends this with specific fields.
 */
@Getter
@Setter
public abstract class BaseGameRound {
    
    protected int roundNumber;
    protected String winner;
    protected boolean revealed;
    protected long roundStartTime;
    
    protected BaseGameRound() {
        this.roundStartTime = System.currentTimeMillis();
    }
    
    protected BaseGameRound(int roundNumber) {
        this.roundNumber = roundNumber;
        this.revealed = false;
        this.roundStartTime = System.currentTimeMillis();
    }
    
    /**
     * Calculate and return the winner of this round
     */
    public abstract String calculateWinner();
    
    /**
     * Check if a player has submitted their answer/guess
     */
    public abstract boolean hasPlayerAnswered(String playerName);
    
    /**
     * Get the number of players who have answered
     */
    public abstract int getAnsweredCount();
}
