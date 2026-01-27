package com.example.funfridaygame.service.game;

import org.springframework.stereotype.Component;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.WordScrambleConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.WordScrambleRound;
import com.example.funfridaygame.service.WordService;

/**
 * Strategy implementation for Word Scramble game.
 */
@Component
public class WordScrambleStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "word-scramble";
    private static final int DEFAULT_WORD_LENGTH = 4;

    private final WordService wordService;

    public WordScrambleStrategy(WordService wordService) {
        this.wordService = wordService;
    }

    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }

    @Override
    public BaseGameConfig createDefaultConfig() {
        return new WordScrambleConfig();
    }

    @Override
    public BaseGameRound createRound(Game game) {
        game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);

        WordScrambleConfig config = game.getTypedConfig(WordScrambleConfig.class);
        int wordLength = config != null ? config.getWordLength() : DEFAULT_WORD_LENGTH;

        String word = wordService.getRandomWord(wordLength);
        String scrambled = wordService.scrambleWord(word);
        return new WordScrambleRound(game.getCurrentRoundNumber(), word, scrambled);
    }

    @Override
    public GameState getActiveGameState() {
        return GameState.GUESSING;
    }

    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof WordScrambleRound round)) {
            return;
        }

        String winner = round.getWinner();
        for (Player player : game.getPlayers()) {
            String guess = round.getPlayerWordGuesses().get(player.getName());
            if (guess != null && guess.equalsIgnoreCase(round.getOriginalWord())) {
                if (player.getName().equals(winner)) {
                    player.setScore(player.getScore() + 100);
                } else {
                    player.setScore(player.getScore() + 50);
                }
            }
        }
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        return round.getAnsweredCount() >= game.getPlayers().size();
    }

    @Override
    public boolean shouldEndRoundEarly(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof WordScrambleRound round)) {
            return false;
        }

        for (String guess : round.getPlayerWordGuesses().values()) {
            if (round.isCorrectGuess(guess)) {
                return true;
            }
        }

        return allPlayersAnswered(game, round);
    }
}
