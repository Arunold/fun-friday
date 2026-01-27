package com.example.funfridaygame.model.round;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Round implementation for Number Guess game.
 * Players guess a target number, closest guess wins.
 */
@Getter
@Setter
public class NumberGuessRound extends BaseGameRound {
    
    private int targetNumber;
    private int minRange;
    private int maxRange;
    private final Map<String, Integer> playerGuesses = new ConcurrentHashMap<>();
    
    public NumberGuessRound() {
        super();
    }
    
    public NumberGuessRound(int roundNumber, int minRange, int maxRange) {
        super(roundNumber);
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.targetNumber = generateRandomNumber(minRange, maxRange);
    }
    
    private int generateRandomNumber(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }
    
    public void addGuess(String playerName, int guess) {
        playerGuesses.put(playerName, guess);
    }
    
    @Override
    public boolean hasPlayerAnswered(String playerName) {
        return playerGuesses.containsKey(playerName);
    }
    
    @Override
    public int getAnsweredCount() {
        return playerGuesses.size();
    }
    
    @Override
    public String calculateWinner() {
        if (playerGuesses.isEmpty()) {
            return null;
        }
        
        String closestPlayer = null;
        int closestDiff = Integer.MAX_VALUE;
        
        for (Map.Entry<String, Integer> entry : playerGuesses.entrySet()) {
            int diff = Math.abs(entry.getValue() - targetNumber);
            if (diff < closestDiff) {
                closestDiff = diff;
                closestPlayer = entry.getKey();
            }
        }
        
        this.winner = closestPlayer;
        return closestPlayer;
    }
}
