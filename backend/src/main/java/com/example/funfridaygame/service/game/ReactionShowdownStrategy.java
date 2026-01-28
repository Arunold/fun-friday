package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.ReactionShowdownConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.ReactionShowdownRound;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for Reaction Showdown game. Players test their reaction time by tapping
 * when the signal appears.
 */
@Component
public class ReactionShowdownStrategy implements GameStrategy {

    private static final String GAME_TYPE_ID = "reaction-showdown";

    @Override
    public String getGameTypeId() {
        return GAME_TYPE_ID;
    }

    @Override
    public BaseGameConfig createDefaultConfig() {
        ReactionShowdownConfig config = new ReactionShowdownConfig();
        config.setRoundDuration(10); // Short rounds for reaction game
        return config;
    }

    @Override
    public BaseGameRound createRound(Game game) {
        game.setCurrentRoundNumber(game.getCurrentRoundNumber() + 1);

        ReactionShowdownConfig config = game.getTypedConfig(ReactionShowdownConfig.class);
        int minDelay = config != null ? config.getMinDelay() : 2000;
        int maxDelay = config != null ? config.getMaxDelay() : 5000;
        boolean includeFakeOuts = config != null && config.isIncludeFakeOuts();
        int fakeOutChance = config != null ? config.getFakeOutChance() : 20;

        return new ReactionShowdownRound(
                game.getCurrentRoundNumber(), minDelay, maxDelay, includeFakeOuts, fakeOutChance);
    }

    @Override
    public GameState getActiveGameState() {
        return GameState.GUESSING;
    }

    @Override
    public void awardPoints(Game game, BaseGameRound baseRound) {
        if (!(baseRound instanceof ReactionShowdownRound round)) {
            return;
        }

        String fastestPlayer = round.findFastestPlayer();

        for (Player player : game.getPlayers()) {
            int points = round.calculatePlayerPoints(player.getName(), fastestPlayer);
            int newScore = Math.max(0, player.getScore() + points);
            player.setScore(newScore);
        }
    }

    @Override
    public boolean allPlayersAnswered(Game game, BaseGameRound round) {
        // Round ends when all players have tapped or timer expires
        return round.getAnsweredCount() >= game.getPlayers().size();
    }
}
