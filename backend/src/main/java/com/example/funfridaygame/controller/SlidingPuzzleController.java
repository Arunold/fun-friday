package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.PuzzleMoveRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.SlidingPuzzleRound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Controller for Sliding Puzzle game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class SlidingPuzzleController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoundManager roundManager;

    public SlidingPuzzleController(
            SimpMessagingTemplate messagingTemplate, GameRoundManager roundManager) {
        this.messagingTemplate = messagingTemplate;
        this.roundManager = roundManager;
    }

    @MessageMapping("/puzzleMove")
    public void handlePuzzleMove(PuzzleMoveRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof SlidingPuzzleRound puzzleRound)) {
            return;
        }

        // Ensure player is initialized
        puzzleRound.initializePlayer(request.getPlayerName());

        // Process the move
        boolean solved = puzzleRound.makeMove(request.getPlayerName(), request.getTileIndex());

        // Update round info and broadcast
        game.updateCurrentRoundInfo();
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        // If this player just solved, check if all players are done
        if (solved) {
            log.info(
                    "Player {} solved the puzzle! Position: {}",
                    request.getPlayerName(),
                    puzzleRound.getSolveOrder().size());

            if (puzzleRound.allPlayersSolved(game.getPlayers().size())) {
                log.info("All players solved! Revealing immediately.");
                roundManager.cancelRoundTimer(gameId);
                roundManager.revealRoundResult(gameId, round.getRoundNumber());
            }
        }
    }
}
