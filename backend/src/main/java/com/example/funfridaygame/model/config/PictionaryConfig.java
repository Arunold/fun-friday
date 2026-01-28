package com.example.funfridaygame.model.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuration for Pictionary game. Handles turn tracking (each player draws once per round). */
@Getter
@Setter
@NoArgsConstructor
public class PictionaryConfig extends BaseGameConfig {

    private volatile int currentTurn = 0;
    private volatile int totalTurns = 0;

    @Override
    public void onRoundStart(int playerCount) {
        // Reset turns when a new round starts
        if (currentTurn == 0 || currentTurn >= totalTurns) {
            currentTurn = 0;
            totalTurns = playerCount;
        }
    }

    @Override
    public void onRoundEnd() {
        // Increment turn after each turn ends
    }

    /** Move to next turn */
    public void nextTurn() {
        currentTurn++;
    }

    /** Check if there are more turns in current round */
    public boolean hasMoreTurns() {
        return currentTurn < totalTurns;
    }

    /** Reset turns for a new round */
    public void resetTurnsForRound(int playerCount) {
        this.currentTurn = 0;
        this.totalTurns = playerCount;
    }
}
