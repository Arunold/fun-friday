package com.example.funfridaygame.model.round;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Round implementation for Word Scramble game. Players unscramble a word, fastest correct answer
 * wins.
 */
@Getter
@Setter
public class WordScrambleRound extends BaseGameRound {

    private String originalWord;
    private String scrambledWord;
    private final Map<String, String> playerWordGuesses = new ConcurrentHashMap<>();
    private final Map<String, Long> playerAnswerTimes = new ConcurrentHashMap<>();

    public WordScrambleRound() {
        super();
    }

    public WordScrambleRound(int roundNumber, String originalWord, String scrambledWord) {
        super(roundNumber);
        this.originalWord = originalWord;
        this.scrambledWord = scrambledWord;
    }

    public void addWordGuess(String playerName, String guess) {
        playerWordGuesses.put(playerName, guess.toUpperCase());
        playerAnswerTimes.put(playerName, System.currentTimeMillis());
    }

    @Override
    public boolean hasPlayerAnswered(String playerName) {
        return playerWordGuesses.containsKey(playerName);
    }

    @Override
    public int getAnsweredCount() {
        return playerWordGuesses.size();
    }

    @Override
    public String calculateWinner() {
        String fastestPlayer = null;
        long fastestTime = Long.MAX_VALUE;

        for (Map.Entry<String, String> entry : playerWordGuesses.entrySet()) {
            String playerName = entry.getKey();
            String guess = entry.getValue();

            // Only correct guesses count
            if (guess.equalsIgnoreCase(originalWord)) {
                Long answerTime = playerAnswerTimes.get(playerName);
                if (answerTime != null && answerTime < fastestTime) {
                    fastestTime = answerTime;
                    fastestPlayer = playerName;
                }
            }
        }

        this.winner = fastestPlayer;
        return fastestPlayer;
    }

    /** Check if the guess is correct */
    public boolean isCorrectGuess(String guess) {
        return originalWord != null && originalWord.equalsIgnoreCase(guess.trim());
    }
}
