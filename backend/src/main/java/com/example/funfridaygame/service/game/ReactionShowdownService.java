package com.example.funfridaygame.service.game;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.ReactionShowdownRound;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service to handle Reaction Showdown game-specific logic. Manages signal scheduling and tap
 * processing.
 */
@Slf4j
@Service
public class ReactionShowdownService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public ReactionShowdownService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdownNow();
    }

    /** Start the round - schedule the signal after a random delay */
    public void startRound(Game game) {
        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof ReactionShowdownRound rsRound)) {
            return;
        }

        int delay = rsRound.getSignalDelay();
        log.info("Reaction round starting, signal will appear in {}ms", delay);

        scheduler.schedule(() -> triggerSignal(game), delay, TimeUnit.MILLISECONDS);
    }

    /** Handle a player's reaction tap */
    public void handleTap(Game game, String playerName) {
        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof ReactionShowdownRound rsRound)) {
            return;
        }

        long serverTapTime = System.currentTimeMillis();
        rsRound.recordTap(playerName, serverTapTime);

        log.info(
                "Player {} tapped at {}ms, phase: {}",
                playerName,
                serverTapTime,
                rsRound.getPhase());

        game.updateCurrentRoundInfo();
        messagingTemplate.convertAndSend("/topic/game/" + game.getGameId(), game);
    }

    private void triggerSignal(Game game) {
        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof ReactionShowdownRound rsRound)) {
            return;
        }

        rsRound.triggerSignal();
        log.info(
                "Signal triggered! Phase: {}, isFakeOut: {}",
                rsRound.getPhase(),
                rsRound.isFakeOut());

        game.updateCurrentRoundInfo();
        messagingTemplate.convertAndSend("/topic/game/" + game.getGameId(), game);
    }
}
