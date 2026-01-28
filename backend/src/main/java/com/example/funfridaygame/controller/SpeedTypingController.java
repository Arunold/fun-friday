package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.TypingProgressRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.SpeedTypingRound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Controller for Speed Typing game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class SpeedTypingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoundManager roundManager;

    public SpeedTypingController(
            SimpMessagingTemplate messagingTemplate, GameRoundManager roundManager) {
        this.messagingTemplate = messagingTemplate;
        this.roundManager = roundManager;
    }

    @MessageMapping("/typingProgress")
    public void updateTypingProgress(TypingProgressRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof SpeedTypingRound stRound)) {
            return;
        }

        boolean finished = stRound.updateProgress(request.getPlayerName(), request.getTypedText());

        // Update round info and broadcast
        game.updateCurrentRoundInfo();
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        // If this player just finished, check if all players are done
        if (finished && stRound.allPlayersFinished(game.getPlayers().size())) {
            log.info("All players finished typing! Revealing immediately.");
            roundManager.cancelRoundTimer(gameId);
            roundManager.revealRoundResult(gameId, round.getRoundNumber());
        }
    }
}
