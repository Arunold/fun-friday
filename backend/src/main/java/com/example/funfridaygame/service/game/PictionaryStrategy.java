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
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for Pictionary game.
 */
@Component
public class PictionaryStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "pictionary";
    private static final int GUESSER_POINTS = 100;
    private static final int DRAWER_POINTS = 50;

    private final PictionaryWordService pictionaryWordService;

    public PictionaryStrategy(PictionaryWordService pictionaryWordService) {
        this.pictionaryWordService = pictionaryWordService;
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

        String correctGuesser = round.getCorrectGuesser();
        String drawerName = round.getDrawerName();

        if (correctGuesser != null) {
            for (Player player : game.getPlayers()) {
                if (player.getName().equals(correctGuesser)) {
                    player.setScore(player.getScore() + GUESSER_POINTS);
                }
                if (player.getName().equals(drawerName)) {
                    player.setScore(player.getScore() + DRAWER_POINTS);
                }
            }
        }
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        return false;
    }

    @Override
    public boolean shouldEndRoundEarly(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof PictionaryRound round)) {
            return false;
        }
        return round.getCorrectGuesser() != null;
    }

    /**
     * Create a turn result for the current turn
     */
    public TurnResult createTurnResult(Game game, PictionaryRound round) {
        PictionaryConfig config = game.getTypedConfig(PictionaryConfig.class);
        int turnNumber = config != null ? config.getCurrentTurn() : 1;

        return new TurnResult(
            turnNumber,
            round.getDrawerName(),
            round.getWordToDraw(),
            round.getCorrectGuesser()
        );
    }

    /**
     * Check if there are more turns in the current round
     */
    public boolean hasMoreTurns(Game game) {
        PictionaryConfig config = game.getTypedConfig(PictionaryConfig.class);
        return config != null && config.hasMoreTurns();
    }
}
