package com.example.funfridaygame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of a single turn in a Pictionary round.
 * Each player gets one turn to draw per round.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnResult {
    private int turnNumber;
    private String drawerName;
    private String wordToDraw;
    private String correctGuesser;
    private int drawerPoints;
    private int guesserPoints;

    public TurnResult(int turnNumber, String drawerName, String wordToDraw, String correctGuesser) {
        this.turnNumber = turnNumber;
        this.drawerName = drawerName;
        this.wordToDraw = wordToDraw;
        this.correctGuesser = correctGuesser;
        
        // Calculate points
        if (correctGuesser != null) {
            this.guesserPoints = 100;
            this.drawerPoints = 50;
        } else {
            this.guesserPoints = 0;
            this.drawerPoints = 0;
        }
    }
}
