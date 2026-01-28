package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.ClearCanvasRequest;
import com.example.funfridaygame.dto.DrawingStrokeRequest;
import com.example.funfridaygame.dto.PictionaryGuessRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.TurnResult;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.PictionaryRound;
import com.example.funfridaygame.service.game.GameStrategyRegistry;
import com.example.funfridaygame.service.game.PictionaryStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Controller for Pictionary game-specific WebSocket endpoints. */
@Slf4j
@Controller
public class PictionaryController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameStrategyRegistry strategyRegistry;
    private final GameRoundManager roundManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public PictionaryController(
            SimpMessagingTemplate messagingTemplate,
            GameStrategyRegistry strategyRegistry,
            GameRoundManager roundManager) {
        this.messagingTemplate = messagingTemplate;
        this.strategyRegistry = strategyRegistry;
        this.roundManager = roundManager;
    }

    @MessageMapping("/draw")
    public void handleDrawingStroke(DrawingStrokeRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)
                || !pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        pRound.addDrawingStroke(request.getStroke());

        Map<String, Object> strokeMessage = new HashMap<>();
        strokeMessage.put("type", "DRAWING_STROKE");
        strokeMessage.put("stroke", request.getStroke());
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/drawing", strokeMessage);
    }

    @MessageMapping("/clearCanvas")
    public void handleClearCanvas(ClearCanvasRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)
                || !pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        pRound.clearDrawing();

        Map<String, Object> clearMessage = new HashMap<>();
        clearMessage.put("type", "CLEAR_CANVAS");
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/drawing", clearMessage);
    }

    @MessageMapping("/undoStroke")
    public void handleUndoStroke(ClearCanvasRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)
                || !pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        Map<String, Object> undoMessage = new HashMap<>();
        undoMessage.put("type", "UNDO_STROKE");
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/drawing", undoMessage);
    }

    @MessageMapping("/redoStroke")
    public void handleRedoStroke(DrawingStrokeRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)
                || !pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        Map<String, Object> redoMessage = new HashMap<>();
        redoMessage.put("type", "REDO_STROKE");
        redoMessage.put("stroke", request.getStroke());
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/drawing", redoMessage);
    }

    @MessageMapping("/pictionaryGuess")
    public void submitPictionaryGuess(PictionaryGuessRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)) {
            return;
        }

        if (pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        pRound.addPictionaryGuess(request.getPlayerName(), request.getGuess());

        Map<String, Object> guessMessage = new HashMap<>();
        guessMessage.put("type", "PICTIONARY_GUESS");
        guessMessage.put("playerName", request.getPlayerName());
        guessMessage.put("guess", request.getGuess());
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/guesses", guessMessage);

        if (pRound.isCorrectPictionaryGuess(request.getGuess())) {
            pRound.setCorrectGuesser(request.getPlayerName());
            roundManager.cancelRoundTimer(gameId);
            PictionaryConfig pConfig = game.getTypedConfig(PictionaryConfig.class);
            int currentTurn = pConfig != null ? pConfig.getCurrentTurn() : 0;
            endPictionaryTurn(gameId, pRound.getRoundNumber(), currentTurn);
        }
    }

    /** End the current Pictionary turn and process results. */
    public synchronized void endPictionaryTurn(String gameId, int roundNumber, int turnNumber) {
        Game game = GameTypeController.getGame(gameId);
        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }
        if (game.getCurrentRound().getRoundNumber() != roundNumber) {
            return;
        }

        PictionaryConfig pConfig = game.getTypedConfig(PictionaryConfig.class);
        if (pConfig == null || pConfig.getCurrentTurn() != turnNumber) {
            return;
        }

        roundManager.cancelRoundTimer(gameId);

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)) {
            return;
        }

        Optional<PictionaryStrategy> strategyOpt =
                strategyRegistry
                        .getStrategy("pictionary")
                        .filter(s -> s instanceof PictionaryStrategy)
                        .map(s -> (PictionaryStrategy) s);

        if (strategyOpt.isEmpty()) {
            return;
        }

        PictionaryStrategy strategy = strategyOpt.get();
        strategy.awardPoints(game, pRound);

        TurnResult turnResult = strategy.createTurnResult(game, pRound);
        pRound.addTurnResult(turnResult);

        pRound.setRevealed(true);
        game.setGameState(GameState.ROUND_RESULT);
        game.setCurrentRound(pRound);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        scheduler.schedule(
                () -> {
                    if (pConfig.hasMoreTurns()) {
                        pRound.setRevealed(false);
                        roundManager.startNewRound(gameId);
                    } else if (game.hasMoreRounds()) {
                        roundManager.startNewRound(gameId);
                    } else {
                        roundManager.finishGame(gameId);
                    }
                },
                5,
                TimeUnit.SECONDS);
    }
}
