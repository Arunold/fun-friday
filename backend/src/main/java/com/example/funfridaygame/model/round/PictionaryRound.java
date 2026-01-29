package com.example.funfridaygame.model.round;

import com.example.funfridaygame.model.TurnResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;

/** Round implementation for Pictionary game. Each player draws once per round, others guess. */
@Getter
@Setter
public class PictionaryRound extends BaseGameRound {

    /** Data class to track player guess information */
    @Getter
    @Setter
    public static class GuessInfo {
        private String guess;
        private long timestamp;
        private int score;

        public GuessInfo(String guess, long timestamp, int score) {
            this.guess = guess;
            this.timestamp = timestamp;
            this.score = score;
        }
    }

    private volatile String drawerName;
    private volatile String wordToDraw;
    private final List<Object> drawingData = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> playerPictionaryGuesses = new ConcurrentHashMap<>();
    private final Map<String, GuessInfo> playerBestGuesses = new ConcurrentHashMap<>();
    private volatile String correctGuesser;
    private final Set<String> correctGuessers = ConcurrentHashMap.newKeySet();
    private volatile int currentTurnNumber = 1;
    private final List<TurnResult> turnResults = new CopyOnWriteArrayList<>();

    public PictionaryRound() {
        super();
    }

    public PictionaryRound(int roundNumber, String drawerName, String wordToDraw) {
        super(roundNumber);
        this.drawerName = drawerName;
        this.wordToDraw = wordToDraw;
    }

    public void addDrawingStroke(Object stroke) {
        this.drawingData.add(stroke);
    }

    public void clearDrawing() {
        this.drawingData.clear();
    }

    public void addPictionaryGuess(String playerName, String guess) {
        playerPictionaryGuesses
                .computeIfAbsent(playerName, k -> new CopyOnWriteArrayList<>())
                .add(guess);
    }

    public boolean isCorrectPictionaryGuess(String guess) {
        return wordToDraw != null && wordToDraw.equalsIgnoreCase(guess.trim());
    }

    public void addTurnResult(TurnResult result) {
        this.turnResults.add(result);
    }

    /** Reset round state for a new turn (new drawer) */
    public void resetForNewTurn(String newDrawer, String newWord) {
        this.drawerName = newDrawer;
        this.wordToDraw = newWord;
        this.drawingData.clear();
        this.playerPictionaryGuesses.clear();
        this.playerBestGuesses.clear();
        this.correctGuesser = null;
        this.correctGuessers.clear();
        this.currentTurnNumber++;
        this.roundStartTime = System.currentTimeMillis();
    }

    @Override
    public boolean hasPlayerAnswered(String playerName) {
        return playerPictionaryGuesses.containsKey(playerName);
    }

    @Override
    public int getAnsweredCount() {
        return playerPictionaryGuesses.size();
    }

    @Override
    public String calculateWinner() {
        // In Pictionary, the winner is set when someone guesses correctly
        this.winner = correctGuesser;
        return correctGuesser;
    }

    public void setCorrectGuesser(String correctGuesser) {
        if (this.correctGuesser == null) {
            this.correctGuesser = correctGuesser;
            this.winner = correctGuesser;
        }
        if (correctGuesser != null) {
            this.correctGuessers.add(correctGuesser);
        }
    }

    public boolean hasPlayerGuessedCorrect(String playerName) {
        return correctGuessers.contains(playerName);
    }
}
