package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.ClearCanvasRequest;
import com.example.funfridaygame.dto.CreateGameRequest;
import com.example.funfridaygame.dto.DrawingStrokeRequest;
import com.example.funfridaygame.dto.GameMessage;
import com.example.funfridaygame.dto.GuessRequest;
import com.example.funfridaygame.dto.JoinGameRequest;
import com.example.funfridaygame.dto.PictionaryGuessRequest;
import com.example.funfridaygame.dto.TypingProgressRequest;
import com.example.funfridaygame.dto.WordGuessRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.GameType;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.TurnResult;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.NumberGuessConfig;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.config.WordScrambleConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.NumberGuessRound;
import com.example.funfridaygame.model.round.PictionaryRound;
import com.example.funfridaygame.model.round.SpeedTypingRound;
import com.example.funfridaygame.model.round.WordScrambleRound;
import com.example.funfridaygame.service.GameTypeRegistry;
import com.example.funfridaygame.service.game.GameStrategy;
import com.example.funfridaygame.service.game.GameStrategyRegistry;
import com.example.funfridaygame.service.game.PictionaryStrategy;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
public class GameController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameTypeRegistry gameTypeRegistry;
    private final GameStrategyRegistry strategyRegistry;
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> roundTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public GameController(SimpMessagingTemplate messagingTemplate,
                         GameTypeRegistry gameTypeRegistry,
                         GameStrategyRegistry strategyRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.gameTypeRegistry = gameTypeRegistry;
        this.strategyRegistry = strategyRegistry;
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down game scheduler...");
        roundTimers.values().forEach(timer -> timer.cancel(true));
        roundTimers.clear();
        scheduler.shutdownNow();
    }

    @MessageMapping("/create")
    public void createGame(CreateGameRequest request) {
        String gameId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Optional<GameType> gameType = gameTypeRegistry.getGameTypeById(request.getGameTypeId());
        if (gameType.isEmpty()) {
            sendError(request.getHostName(), "Invalid game type");
            return;
        }

        Optional<GameStrategy> strategyOpt = strategyRegistry.getStrategy(request.getGameTypeId());
        if (strategyOpt.isEmpty()) {
            sendError(request.getHostName(), "No strategy found for game type");
            return;
        }

        Game game = new Game();
        game.setGameId(gameId);
        game.setHost(request.getHostName());
        game.setGameTypeId(request.getGameTypeId());
        game.setGameTypeName(gameType.get().getName());
        game.setTotalRounds(request.getTotalRounds() > 0 ? request.getTotalRounds() : 3);

        // Initialize game-specific config from strategy
        BaseGameConfig config = strategyOpt.get().createDefaultConfig();
        int roundDuration = request.getRoundDuration() > 0 ? request.getRoundDuration() : 30;
        config.setRoundDuration(roundDuration);

        // Apply request-specific settings to config
        if (config instanceof WordScrambleConfig wsConfig) {
            wsConfig.setWordLength(request.getWordLength() > 0 ? request.getWordLength() : 7);
        } else if (config instanceof NumberGuessConfig ngConfig) {
            // Could add min/max range to CreateGameRequest if needed
            ngConfig.setMinRange(1);
            ngConfig.setMaxRange(100);
        } else if (config instanceof PictionaryConfig pConfig) {
            pConfig.setTotalTurns(game.getPlayers().size()); // Will be updated when game starts
        }

        game.setGameConfig(config);

        Player host = Player.builder()
            .name(request.getHostName())
            .avatar(request.getAvatar())
            .build();
        game.getPlayers().add(host);

        games.put(gameId, game);
        GameTypeController.registerGame(gameId, game);

        messagingTemplate.convertAndSend("/topic/created/" + request.getHostName(), game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/join")
    public void joinGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = games.get(gameId);

        if (game == null) {
            sendError(message.getSender(), "Game not found");
            return;
        }

        if (game.getGameState() != GameState.LOBBY) {
            sendError(message.getSender(), "Game already in progress");
            return;
        }

        boolean exists = game.getPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(message.getSender()));

        if (exists) {
            sendError(message.getSender(), "Choose different name. Player already exists in the game with same name.");
            return;
        }

        Player player = Player.builder()
            .name(message.getSender())
            .build();
        game.getPlayers().add(player);

        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/joinWithProfile")
    public void joinGameWithProfile(JoinGameRequest request) {
        String gameId = request.getGameCode().toUpperCase();
        Game game = games.get(gameId);

        if (game == null) {
            sendError(request.getPlayerName(), "Game not found");
            return;
        }

        if (game.getGameState() != GameState.LOBBY) {
            sendError(request.getPlayerName(), "Game already in progress");
            return;
        }

        boolean exists = game.getPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(request.getPlayerName()));

        if (exists) {
            sendError(request.getPlayerName(), "Choose different name. Player already exists in the game with same name.");
            return;
        }

        Player player = Player.builder()
            .name(request.getPlayerName())
            .avatar(request.getAvatar())
            .build();
        game.getPlayers().add(player);

        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/start")
    public void startGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = games.get(gameId);

        if (game != null && game.getHost().equals(message.getSender()) && game.getGameState() == GameState.LOBBY) {
            game.setGameState(GameState.STARTING);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            scheduler.schedule(() -> startNewRound(gameId), 3, TimeUnit.SECONDS);
        }
    }

    @MessageMapping("/guess")
    public void submitGuess(GuessRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof NumberGuessRound ngRound) || ngRound.hasPlayerAnswered(request.getPlayerName())) {
            return;
        }

        ngRound.addGuess(request.getPlayerName(), request.getGuess());
        game.setCurrentRound(round);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        checkRoundEnd(gameId, game);
    }

    @MessageMapping("/wordGuess")
    public void submitWordGuess(WordGuessRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.GUESSING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof WordScrambleRound wsRound) || wsRound.hasPlayerAnswered(request.getPlayerName())) {
            return;
        }

        wsRound.addWordGuess(request.getPlayerName(), request.getGuess());
        game.setCurrentRound(round);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        // Check if correct answer - end round early if correct guess
        if (wsRound.isCorrectGuess(request.getGuess())) {
            cancelRoundTimer(gameId);
            revealRoundResult(gameId, round.getRoundNumber());
        } else {
            checkRoundEnd(gameId, game);
        }
    }

    @MessageMapping("/typingProgress")
    public void updateTypingProgress(TypingProgressRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = games.get(gameId);

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
            cancelRoundTimer(gameId);
            revealRoundResult(gameId, round.getRoundNumber());
        }
    }

    private void checkRoundEnd(String gameId, Game game) {
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

    @MessageMapping("/playAgain")
    public void playAgain(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = games.get(gameId);

        if (game != null && game.getHost().equals(message.getSender()) && game.getGameState() == GameState.FINISHED) {
            game.setGameState(GameState.LOBBY);
            game.setCurrentRoundNumber(0);
            game.setCurrentRound(null);
            game.setCurrentRoundInfo(null);

            // Reset game-specific config state
            BaseGameConfig config = game.getGameConfig();
            if (config instanceof PictionaryConfig pConfig) {
                pConfig.resetTurnsForRound(game.getPlayers().size());
            }

            for (Player player : game.getPlayers()) {
                player.setScore(0);
            }

            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
        }
    }

    private void startNewRound(String gameId) {
        Game game = games.get(gameId);
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

        int roundDuration = config != null ? config.getRoundDuration() : 30;
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            if (strategy instanceof PictionaryStrategy) {
                endPictionaryTurn(gameId, roundNumber, currentTurnNumber);
            } else {
                revealRoundResult(gameId, roundNumber);
            }
        }, roundDuration, TimeUnit.SECONDS);
        roundTimers.put(gameId, timer);
    }

    // ==================== Pictionary-specific handlers ====================

    @MessageMapping("/draw")
    public void handleDrawingStroke(DrawingStrokeRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound) || !pRound.getDrawerName().equals(request.getPlayerName())) {
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
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound) || !pRound.getDrawerName().equals(request.getPlayerName())) {
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
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound) || !pRound.getDrawerName().equals(request.getPlayerName())) {
            return;
        }

        Map<String, Object> undoMessage = new HashMap<>();
        undoMessage.put("type", "UNDO_STROKE");
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/drawing", undoMessage);
    }

    @MessageMapping("/redoStroke")
    public void handleRedoStroke(DrawingStrokeRequest request) {
        String gameId = request.getGameId().toUpperCase();
        Game game = games.get(gameId);

        if (game == null || game.getGameState() != GameState.DRAWING) {
            return;
        }

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound) || !pRound.getDrawerName().equals(request.getPlayerName())) {
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
        Game game = games.get(gameId);

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
            cancelRoundTimer(gameId);
            PictionaryConfig pConfig = game.getTypedConfig(PictionaryConfig.class);
            int currentTurn = pConfig != null ? pConfig.getCurrentTurn() : 0;
            endPictionaryTurn(gameId, pRound.getRoundNumber(), currentTurn);
        }
    }

    private synchronized void endPictionaryTurn(String gameId, int roundNumber, int turnNumber) {
        Game game = games.get(gameId);
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

        cancelRoundTimer(gameId);

        BaseGameRound round = game.getCurrentRound();
        if (!(round instanceof PictionaryRound pRound)) {
            return;
        }

        Optional<GameStrategy> strategyOpt = strategyRegistry.getStrategy(game.getGameTypeId());
        if (strategyOpt.isEmpty()) {
            return;
        }

        PictionaryStrategy strategy = (PictionaryStrategy) strategyOpt.get();
        strategy.awardPoints(game, pRound);

        TurnResult turnResult = strategy.createTurnResult(game, pRound);
        pRound.addTurnResult(turnResult);

        pRound.setRevealed(true);
        game.setGameState(GameState.ROUND_RESULT);
        game.setCurrentRound(pRound);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);

        scheduler.schedule(() -> {
            if (pConfig.hasMoreTurns()) {
                pRound.setRevealed(false);
                startNewRound(gameId);
            } else if (game.hasMoreRounds()) {
                startNewRound(gameId);
            } else {
                finishGame(gameId);
            }
        }, 5, TimeUnit.SECONDS);
    }

    // ==================== Common round handling ====================

    private void cancelRoundTimer(String gameId) {
        ScheduledFuture<?> timer = roundTimers.remove(gameId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private synchronized void revealRoundResult(String gameId, int roundNumber) {
        Game game = games.get(gameId);
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

        scheduler.schedule(() -> {
            if (game.hasMoreRounds()) {
                startNewRound(gameId);
            } else {
                finishGame(gameId);
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void finishGame(String gameId) {
        Game game = games.get(gameId);
        if (game == null) {
            return;
        }

        game.setGameState(GameState.FINISHED);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    // ==================== Player management ====================

    @MessageMapping("/leave")
    public void leaveGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = games.get(gameId);

        if (game == null) {
            return;
        }

        String playerName = message.getSender();
        boolean isHost = game.getHost().equals(playerName);

        if (isHost) {
            cancelRoundTimer(gameId);
            games.remove(gameId);
            GameTypeController.removeGame(gameId);

            Map<String, Object> hostLeftMessage = new HashMap<>();
            hostLeftMessage.put("type", "HOST_LEFT");
            hostLeftMessage.put("message", "The host has left the game. Game terminated.");
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/terminated", hostLeftMessage);

            log.info("Host {} left game {}. Game terminated.", playerName, gameId);
        } else {
            game.getPlayers().removeIf(p -> p.getName().equals(playerName));
            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            log.info("Player {} left game {}", playerName, gameId);
        }
    }

    @MessageMapping("/removePlayer")
    public void removePlayer(GameMessage message) {
        String[] parts = message.getContent().split(":");
        if (parts.length != 2) {
            return;
        }

        String gameId = parts[0].toUpperCase();
        String playerToRemove = parts[1];
        String hostName = message.getSender();

        Game game = games.get(gameId);
        if (game == null) {
            return;
        }

        if (!game.getHost().equals(hostName)) {
            log.warn("Non-host {} tried to remove player {}", hostName, playerToRemove);
            return;
        }

        if (game.getHost().equals(playerToRemove)) {
            log.warn("Cannot remove host from game");
            return;
        }

        boolean removed = game.getPlayers().removeIf(p -> p.getName().equals(playerToRemove));

        if (removed) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/removed/" + playerToRemove,
                    Map.of("type", "REMOVED", "message", "You have been removed from the game by the host."));
            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            log.info("Host {} removed player {} from game {}", hostName, playerToRemove, gameId);
        }
    }

    private void sendError(String username, String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        messagingTemplate.convertAndSend("/topic/error/" + username, error);
    }
}
