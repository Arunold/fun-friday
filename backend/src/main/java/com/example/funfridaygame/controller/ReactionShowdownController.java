package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.ReactionTapRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.service.game.ReactionShowdownService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Controller for Reaction Showdown game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class ReactionShowdownController {

    private final ReactionShowdownService reactionService;

    public ReactionShowdownController(ReactionShowdownService reactionService) {
        this.reactionService = reactionService;
    }

    @MessageMapping("/reactionTap")
    public void handleReactionTap(ReactionTapRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game != null) {
            reactionService.handleTap(game, request.getPlayerName());
        }
    }
}
