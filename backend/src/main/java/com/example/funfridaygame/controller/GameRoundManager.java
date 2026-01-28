package com.example.funfridaygame.controller;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.service.game.GameStrategy;
import com.example.funfridaygame.service.game.GameStrategyRegistry;
import com.example.funfridaygame.service.game.PictionaryStrategy;
import com.example.funfridaygame.service.game.ReactionShowdownService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Manages game round lifecycle including starting, timing, and ending rounds. Extracted from
 * GameController to be shared across game-specific controllers.
 */
@Slf4j
@Component
public class GameRoundManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameStrategyRegistry strategyRegistry;
    private final ReactionShowdownService reactionService;
    private final PictionaryController pictionaryController;
    private final Map<String, ScheduledFuture<?>> roundTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public GameRoundManager(
            SimpMessagingTemplate messagingTemplate,
            GameStrategyRegistry strategyRegistry,
            ReactionShowdownService reactionService,
            @Lazy PictionaryController pictionaryController) {
        this.messagingTemplate = messagingTemplate;
        this.strategyRegistry = strategyRegistry;
        this.reactionService = reactionService;
        this.pictionaryController = pictionaryController;
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down game round manager scheduler...");
        roundTimers.values().forEach(timer -> timer.cancel(true));
        roundTimers.clear();
        scheduler.shutdownNow();
    }

    /** Cancel the round timer for a game. */
    public void cancelRoundTimer(String gameId) {
        ScheduledFuture<?> timer = roundTimers.remove(gameId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    /** Check if the round should end early based on game strategy. */
    public void checkRoundEnd(String gameId, Game game) {
        Optional<GameStrategy> strategyOpt = strategyRegistry.getStrategy(game.getGameTypeId());
        if (strategyOpt.isEmpty()) {
            return;
        }

        GameStrategy strategy = strategyOpt.get();
        if (strategy.shouldEndRoundEarly(game, game.getCurrentRound())) {
            cancelRoundTimer(gameId);
            revealRoundResult(gameId, game.getCurrentRound().getRoundNumber());
        }
    }

    /** Start a new round for the game. */
    public void startNewRound(String gameId) {
        Game game = GameTypeController.getGame(gameId);
        if (game == null) {
            return;
        }

        cancelRoundTimer(gameId);

        Optional<GameStrategy> strategyOpt = strategyRegistry.getStrategy(game.getGameTypeId());
        if (strategyOpt.isEmpty()) {
            log.error("No strategy found for game type: {}", game.getGameTypeId());
            return;
        }

        GameStrategy strategy = strategyOpt.get();
        BaseGameRound round = strategy.createRound(game);
        game.setCurrentRound(round);
        game.setGameState(strategy.getActiveGameState());

        int roundNumber = game.getCurrentRoundNumber();
        int turnNumber = 0;
        BaseGameConfig config = game.getGameConfig();
        if (config instanceof PictionaryConfig pConfig) {
            turnNumber = pConfig.getCurrentTurn();
        }
        final int currentTurnNumber = turnNumber;

        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        // For Reaction Showdown, trigger the signal scheduling
        if ("reaction-showdown".equals(game.getGameTypeId())) {
            reactionService.startRound(game);
        }

        int roundDuration = config != null ? config.getRoundDuration() : 30;
        ScheduledFuture<?> timer =
                scheduler.schedule(
                        () -> {
                            if (strategy instanceof PictionaryStrategy) {
                                pictionaryController.endPictionaryTurn(
                                        gameId, roundNumber, currentTurnNumber);
                            } else {
                                revealRoundResult(gameId, roundNumber);
                            }
                        },
                        roundDuration,
                        TimeUnit.SECONDS);
        roundTimers.put(gameId, timer);
    }

    /** Reveal the round result and award points. */
    public synchronized void revealRoundResult(String gameId, int roundNumber) {
        Game game = GameTypeController.getGame(gameId);
        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }
        if (game.getCurrentRound().getRoundNumber() != roundNumber) {
            return;
        }

        cancelRoundTimer(gameId);

        BaseGameRound round = game.getCurrentRound();
        round.setRevealed(true);
        round.calculateWinner();

        Optional<GameStrategy> strategyOpt = strategyRegistry.getStrategy(game.getGameTypeId());
        strategyOpt.ifPresent(strategy -> strategy.awardPoints(game, round));

        game.setGameState(GameState.ROUND_RESULT);
        game.setCurrentRound(round);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        scheduler.schedule(
                () -> {
                    if (game.hasMoreRounds()) {
                        startNewRound(gameId);
                    } else {
                        finishGame(gameId);
                    }
                },
                5,
                TimeUnit.SECONDS);
    }

    /** Finish the game. */
    public void finishGame(String gameId) {
        Game game = GameTypeController.getGame(gameId);
        if (game == null) {
            return;
        }

        game.setGameState(GameState.FINISHED);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    /** Schedule a task with delay. */
    public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }
}
