package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.TurnResult;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.PictionaryRound;
import com.example.funfridaygame.service.PictionaryWordService;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

/** Strategy implementation for Pictionary game. */
@Component
public class PictionaryStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "pictionary";
    private static final int CORRECT_ANSWER_POINTS = 100;
    private static final int DRAWER_POINTS = 50;
    private static final double SIMILARITY_THRESHOLD = 0.5; // 50% threshold

    private final PictionaryWordService pictionaryWordService;
    private final LevenshteinDistance levenshteinDistance;

    public PictionaryStrategy(PictionaryWordService pictionaryWordService) {
        this.pictionaryWordService = pictionaryWordService;
        this.levenshteinDistance = new LevenshteinDistance();
    }

    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }

    @Override
    public BaseGameConfig createDefaultConfig() {
        return new PictionaryConfig();
    }

    @Override
    public BaseGameRound createRound(Game game) {
        PictionaryConfig config = game.getTypedConfig(PictionaryConfig.class);
        if (config == null) {
            config = new PictionaryConfig();
            game.setGameConfig(config);
        }

        // Check if this is a new round or continuing with next turn
        if (config.getCurrentTurn() == 0 || config.getCurrentTurn() >= config.getTotalTurns()) {
            // New round - increment round number and reset turns
            game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);
            config.resetTurnsForRound(game.getPlayers().size());
        }

        // Start next turn
        config.nextTurn();

        // Select drawer based on current turn (0-indexed)
        int drawerIndex = config.getCurrentTurn() - 1;
        String drawerName = game.getPlayers().get(drawerIndex).getName();

        // Get a random word for the drawer
        String wordToDraw = pictionaryWordService.getRandomWord();

        return new PictionaryRound(game.getCurrentRoundNumber(), drawerName, wordToDraw);
    }

    @Override
    public GameState getActiveGameState() {
        return GameState.DRAWING;
    }

    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof PictionaryRound round)) {
            return;
        }

        String drawerName = round.getDrawerName();
        boolean hasAnyCorrectGuesser = !round.getCorrectGuessers().isEmpty();

        for (Player player : game.getPlayers()) {
            if (player.getName().equals(drawerName) && hasAnyCorrectGuesser) {
                player.setScore(player.getScore() + DRAWER_POINTS);
                continue;
            }

            PictionaryRound.GuessInfo guessInfo =
                    round.getPlayerBestGuesses().get(player.getName());
            if (guessInfo != null && guessInfo.getScore() > 0) {
                player.setScore(player.getScore() + guessInfo.getScore());
            }
        }
    }

    /**
     * Calculate score for a guess based on similarity and time remaining.
     *
     * @param guess The player's guess
     * @param correctAnswer The correct word
     * @param guessTimestamp When the guess was made
     * @param roundStartTime When the round started
     * @param roundDuration Total round duration in seconds
     * @return The calculated score
     */
    public int calculateGuessScore(
            String guess,
            String correctAnswer,
            long guessTimestamp,
            long roundStartTime,
            int roundDuration) {

        // Normalize strings for comparison
        String normalizedGuess = guess.trim().toLowerCase();
        String normalizedAnswer = correctAnswer.trim().toLowerCase();

        // Calculate similarity percentage
        double similarity = calculateSimilarity(normalizedGuess, normalizedAnswer);

        // Calculate time bonus (1 point per second remaining)
        long elapsedMillis = guessTimestamp - roundStartTime;
        long elapsedSeconds = elapsedMillis / 1000;
        int secondsRemaining = Math.max(0, roundDuration - (int) elapsedSeconds);

        // Exact match = 100 + time bonus
        if (normalizedGuess.equals(normalizedAnswer)) {
            return CORRECT_ANSWER_POINTS + secondsRemaining;
        }

        // Similarity below threshold = 0 points
        if (similarity < SIMILARITY_THRESHOLD) {
            return 0;
        }

        // Similarity >= 50% = (similarity% / 2) + time bonus
        int similarityPoints = (int) ((similarity * 100) / 2);
        return similarityPoints + secondsRemaining;
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance.
     *
     * @param str1 First string
     * @param str2 Second string
     * @return Similarity as a value between 0 and 1
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1.equals(str2)) {
            return 1.0;
        }

        int distance = levenshteinDistance.apply(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        if (!(round instanceof PictionaryRound pRound)) {
            return false;
        }

        int totalGuessers = Math.max(0, game.getPlayers().size() - 1);
        if (totalGuessers == 0) {
            return true;
        }

        return pRound.getCorrectGuessers().size() >= totalGuessers;
    }

    @Override
    public boolean shouldEndRoundEarly(Game game, BaseGameRound baseRound) {
        return allPlayersAnswered(game, baseRound);
    }

    /** Create a turn result for the current turn */
    public TurnResult createTurnResult(Game game, PictionaryRound round) {
        PictionaryConfig config = game.getTypedConfig(PictionaryConfig.class);
        int turnNumber = config != null ? config.getCurrentTurn() : 1;

        return new TurnResult(
                turnNumber,
                round.getDrawerName(),
                round.getWordToDraw(),
                round.getCorrectGuesser());
    }

    /** Check if there are more turns in the current round */
    public boolean hasMoreTurns(Game game) {
        PictionaryConfig config = game.getTypedConfig(PictionaryConfig.class);
        return config != null && config.hasMoreTurns();
    }
}
