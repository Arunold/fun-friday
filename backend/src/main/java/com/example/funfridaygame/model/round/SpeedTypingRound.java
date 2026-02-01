package com.example.funfridaygame.model.round;

import com.example.funfridaygame.model.config.SpeedTypingConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Round implementation for Speed Typing Race game. Players race to type a displayed sentence as
 * fast and accurately as possible.
 */
@Getter
@Setter
public class SpeedTypingRound extends BaseGameRound {

    private String targetText;
    private SpeedTypingConfig.Difficulty difficulty;
    private long startTime;
    private int minAccuracy;

    // Player progress: playerName -> PlayerProgress
    private final Map<String, PlayerProgress> playerProgress = new ConcurrentHashMap<>();

    // Ordered list of players who finished
    private final List<String> finishOrder = Collections.synchronizedList(new ArrayList<>());

    // Predefined sentences by difficulty
    private static final List<String> EASY_SENTENCES =
            List.of(
                    "Hello world!",
                    "Fun Friday rocks!",
                    "Type faster now.",
                    "Code is poetry.",
                    "Happy coding day!",
                    "Debug this code.",
                    "Ship it today!",
                    "Git push origin.",
                    "The quick fox.",
                    "Just keep typing.");

    private static final List<String> MEDIUM_SENTENCES =
            List.of(
                    "The quick brown fox jumps over the lazy dog near the park.",
                    "Pack my box with five dozen liquor jugs for the party.",
                    "How vexingly quick daft zebras jump over the wooden fence!",
                    "A wizard's job is to vex chumps quickly in fog and rain.",
                    "Sphinx of black quartz, judge my vow to code better today.",
                    "The five boxing wizards jump quickly through the hazy fog.",
                    "Jackdaws love my big sphinx of quartz in the garden path.",
                    "Crazy Frederick bought many very exquisite opal jewels today.");

    private static final List<String> HARD_SENTENCES =
            List.of(
                    "Programming is not about typing, it's about thinking. But fast typing helps during Fun Friday games!",
                    "The best error message is the one that never shows up. The second best has a helpful stack trace.",
                    "In software development, premature optimization is the root of all evil, but so is premature abstraction.",
                    "Any fool can write code that a computer can understand. Good programmers write code humans can understand.",
                    "First solve the problem, then write the code. Debugging is twice as hard as writing the code in the first place.");

    public SpeedTypingRound() {
        super();
    }

    public SpeedTypingRound(
            int roundNumber, SpeedTypingConfig.Difficulty difficulty, int minAccuracy) {
        super(roundNumber);
        this.difficulty = difficulty;
        this.minAccuracy = minAccuracy;
        this.targetText = selectRandomSentence(difficulty);
        this.startTime = System.currentTimeMillis();
    }

    private String selectRandomSentence(SpeedTypingConfig.Difficulty diff) {
        List<String> sentences =
                switch (diff) {
                    case EASY -> EASY_SENTENCES;
                    case MEDIUM -> MEDIUM_SENTENCES;
                    case HARD -> HARD_SENTENCES;
                };
        return sentences.get(new Random().nextInt(sentences.size()));
    }

    /**
     * Update player's typing progress
     *
     * @return true if player just finished, false otherwise
     */
    public boolean updateProgress(String playerName, String typedText, long timestamp) {
        PlayerProgress progress =
                playerProgress.computeIfAbsent(playerName, k -> new PlayerProgress());

        // Already finished - no update needed
        if (progress.isFinished()) {
            return false;
        }

        progress.setTypedText(typedText);
        progress.setLastUpdateTime(timestamp);

        // Calculate percentage and accuracy
        int targetLength = targetText.length();
        int typedLength = typedText.length();

        progress.setPercentage(Math.min(100, (typedLength * 100) / targetLength));
        progress.setAccuracy(calculateAccuracy(typedText));

        // Check if player finished (100% with required accuracy)
        if (typedLength >= targetLength
                && progress.getAccuracy() >= minAccuracy
                && !progress.isFinished()) {
            progress.setFinished(true);
            progress.setFinishTime(timestamp - startTime);
            finishOrder.add(playerName);
            return true;
        }

        return false;
    }

    /**
     * Update player's typing progress using current time
     *
     * @return true if player just finished, false otherwise
     */
    public boolean updateProgress(String playerName, String typedText) {
        return updateProgress(playerName, typedText, System.currentTimeMillis());
    }

    /** Check if all players have finished typing */
    public boolean allPlayersFinished(int totalPlayers) {
        return finishOrder.size() >= totalPlayers;
    }

    /** Calculate accuracy using character-by-character comparison */
    private int calculateAccuracy(String typedText) {
        if (typedText.isEmpty()) {
            return 100;
        }

        int correctChars = 0;
        int compareLength = Math.min(typedText.length(), targetText.length());

        for (int i = 0; i < compareLength; i++) {
            if (typedText.charAt(i) == targetText.charAt(i)) {
                correctChars++;
            }
        }

        // Penalize for extra characters
        int totalChars = Math.max(typedText.length(), compareLength);
        return (int) ((correctChars * 100.0) / totalChars);
    }

    @Override
    public boolean hasPlayerAnswered(String playerName) {
        PlayerProgress progress = playerProgress.get(playerName);
        return progress != null && progress.isFinished();
    }

    @Override
    public int getAnsweredCount() {
        return (int) playerProgress.values().stream().filter(PlayerProgress::isFinished).count();
    }

    @Override
    public String calculateWinner() {
        if (finishOrder.isEmpty()) {
            // No one finished - pick the player with highest progress
            String bestPlayer = null;
            int bestProgress = -1;

            for (Map.Entry<String, PlayerProgress> entry : playerProgress.entrySet()) {
                int score = entry.getValue().getPercentage() * entry.getValue().getAccuracy();
                if (score > bestProgress) {
                    bestProgress = score;
                    bestPlayer = entry.getKey();
                }
            }
            this.winner = bestPlayer;
            return bestPlayer;
        }

        // First to finish wins
        this.winner = finishOrder.getFirst();
        return this.winner;
    }

    /** Inner class to track player progress */
    @Getter
    @Setter
    public static class PlayerProgress {
        private String typedText = "";
        private int percentage = 0;
        private int accuracy = 100;
        private boolean finished = false;
        private long finishTime = 0;
        private long lastUpdateTime = 0;
    }
}
