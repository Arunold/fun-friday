package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.WordGuessRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.WordScrambleRound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Controller for Word Scramble game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class WordScrambleController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoundManager roundManager;

    public WordScrambleController(
            SimpMessagingTemplate messagingTemplate, GameRoundManager roundManager) {
        this.messagingTemplate = messagingTemplate;
        this.roundManager = roundManager;
    }

    @MessageMapping("/wordGuess")
    public void submitWordGuess(WordGuessRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof WordScrambleRound wsRound)
                || wsRound.hasPlayerAnswered(request.getPlayerName())) {
            return;
        }

        wsRound.addWordGuess(request.getPlayerName(), request.getGuess());
        game.setCurrentRound(round);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        // Check if correct answer - end round early if correct guess
        if (wsRound.isCorrectGuess(request.getGuess())) {
            roundManager.cancelRoundTimer(gameId);
            roundManager.revealRoundResult(gameId, round.getRoundNumber());
        } else {
            roundManager.checkRoundEnd(gameId, game);
        }
    }
}
