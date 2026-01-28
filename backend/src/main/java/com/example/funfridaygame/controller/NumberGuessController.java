package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.GuessRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.NumberGuessRound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Controller for Number Guess game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class NumberGuessController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoundManager roundManager;

    public NumberGuessController(
            SimpMessagingTemplate messagingTemplate, GameRoundManager roundManager) {
        this.messagingTemplate = messagingTemplate;
        this.roundManager = roundManager;
    }

    @MessageMapping("/guess")
    public void submitGuess(GuessRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof NumberGuessRound ngRound)
                || ngRound.hasPlayerAnswered(request.getPlayerName())) {
            return;
        }

        ngRound.addGuess(request.getPlayerName(), request.getGuess());
        game.setCurrentRound(round);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        roundManager.checkRoundEnd(gameId, game);
    }
}
