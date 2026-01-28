package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.NumberGuessConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.NumberGuessRound;
import org.springframework.stereotype.Component;

/** Strategy implementation for Number Guess game. */
@Component
public class NumberGuessStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "number-guess";

    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }

    @Override
    public BaseGameConfig createDefaultConfig() {
        return new NumberGuessConfig();
    }

    @Override
    public BaseGameRound createRound(Game game) {
        game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);

        NumberGuessConfig config = game.getTypedConfig(NumberGuessConfig.class);
        int minRange = config != null ? config.getMinRange() : 1;
        int maxRange = config != null ? config.getMaxRange() : 100;

        return new NumberGuessRound(game.getCurrentRoundNumber(), minRange, maxRange);
    }

    @Override
    public GameState getActiveGameState() {
        return GameState.GUESSING;
    }

    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof NumberGuessRound round)) {
            return;
        }

        for (Player player : game.getPlayers()) {
            Integer guess = round.getPlayerGuesses().get(player.getName());
            if (guess != null) {
                int diff = Math.abs(guess - round.getTargetNumber());
                int points = (diff == 0) ? 100 : Math.max(0, 50 - (diff / 2));
                player.setScore(player.getScore() + points);
            }
        }
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        return round.getAnsweredCount() >= game.getPlayers().size();
    }
}
