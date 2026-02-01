package com.example.funfridaygame.controller;

import com.example.funfridaygame.dto.ChangeGameRequest;
import com.example.funfridaygame.dto.CreateGameRequest;
import com.example.funfridaygame.dto.GameMessage;
import com.example.funfridaygame.dto.JoinGameRequest;
import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameState;
import com.example.funfridaygame.model.GameType;
import com.example.funfridaygame.model.Player;
import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.NumberGuessConfig;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.config.ReactionShowdownConfig;
import com.example.funfridaygame.model.config.SlidingPuzzleConfig;
import com.example.funfridaygame.model.config.SpeedTypingConfig;
import com.example.funfridaygame.model.config.WordScrambleConfig;
import com.example.funfridaygame.service.GameTypeRegistry;
import com.example.funfridaygame.service.game.GameStrategy;
import com.example.funfridaygame.service.game.GameStrategyRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Main game controller handling common game operations. Game-specific handlers are in their
 * respective controllers: - NumberGuessController - WordScrambleController - SpeedTypingController
 * - ReactionShowdownController - PictionaryController
 */
@Slf4j
@Controller
public class GameController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameTypeRegistry gameTypeRegistry;
    private final GameStrategyRegistry strategyRegistry;
    private final GameRoundManager roundManager;
    private final ObjectMapper objectMapper;

    public GameController(
            SimpMessagingTemplate messagingTemplate,
            GameTypeRegistry gameTypeRegistry,
            GameStrategyRegistry strategyRegistry,
            GameRoundManager roundManager,
            ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.gameTypeRegistry = gameTypeRegistry;
        this.strategyRegistry = strategyRegistry;
        this.roundManager = roundManager;
        this.objectMapper = objectMapper;
    }

    // ==================== Game Lifecycle ====================

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
        switch (config) {
            case WordScrambleConfig wsConfig ->
                    wsConfig.setWordLength(
                            request.getWordLength() > 0 ? request.getWordLength() : 7);
            case NumberGuessConfig ngConfig -> {
                ngConfig.setMinRange(request.getMinRange() > 0 ? request.getMinRange() : 1);
                ngConfig.setMaxRange(request.getMaxRange() > 0 ? request.getMaxRange() : 100);
            }
            case PictionaryConfig pConfig -> pConfig.setTotalTurns(game.getPlayers().size());
            case SlidingPuzzleConfig spConfig -> {
                var diff = parseDifficulty(request.getDifficulty());
                spConfig.setDifficulty(diff);
                spConfig.setGridSize(getGridSizeForDifficulty(diff));
                config.setRoundDuration(getDurationForDifficulty(diff, roundDuration));
            }
            case SpeedTypingConfig stConfig -> {
                var diff = parseSpeedTypingDifficulty(request.getDifficulty());
                stConfig.setDifficulty(diff);
            }
            case ReactionShowdownConfig rsConfig ->
                    rsConfig.setIncludeFakeOuts(request.isIncludeFakeOuts());
            default -> {}
        }

        game.setGameConfig(config);

        Player host =
                Player.builder().name(request.getHostName()).avatar(request.getAvatar()).build();
        game.getPlayers().add(host);

        GameTypeController.registerGame(gameId, game);

        messagingTemplate.convertAndSend("/topic/created/" + request.getHostName(), game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/join")
    public void joinGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null) {
            sendError(message.getSender(), "Game not found");
            return;
        }

        if (game.getGameState() != GameState.LOBBY) {
            sendError(message.getSender(), "Game already in progress");
            return;
        }

        boolean exists =
                game.getPlayers().stream()
                        .anyMatch(p -> p.getName().equalsIgnoreCase(message.getSender()));

        if (exists) {
            sendError(
                    message.getSender(),
                    "Choose different name. Player already exists in the game with same name.");
            return;
        }

        Player player = Player.builder().name(message.getSender()).build();
        game.getPlayers().add(player);

        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/joinWithProfile")
    public void joinGameWithProfile(JoinGameRequest request) {
        String gameId = request.getGameCode().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null) {
            sendError(request.getPlayerName(), "Game not found");
            return;
        }

        if (game.getGameState() != GameState.LOBBY) {
            sendError(request.getPlayerName(), "Game already in progress");
            return;
        }

        boolean exists =
                game.getPlayers().stream()
                        .anyMatch(p -> p.getName().equalsIgnoreCase(request.getPlayerName()));

        if (exists) {
            sendError(
                    request.getPlayerName(),
                    "Choose different name. Player already exists in the game with same name.");
            return;
        }

        Player player =
                Player.builder().name(request.getPlayerName()).avatar(request.getAvatar()).build();
        game.getPlayers().add(player);

        messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
    }

    @MessageMapping("/start")
    public void startGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game != null
                && game.getHost().equals(message.getSender())
                && game.getGameState() == GameState.LOBBY) {
            game.setGameState(GameState.STARTING);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            roundManager.scheduleTask(
                    () -> roundManager.startNewRound(gameId), 3, TimeUnit.SECONDS);
        }
    }

    @MessageMapping("/playAgain")
    public void playAgain(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game != null
                && game.getHost().equals(message.getSender())
                && game.getGameState() == GameState.FINISHED) {
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

    @MessageMapping("/changeGame")
    public void changeGame(GameMessage message) {
        try {
            ChangeGameRequest request =
                    objectMapper.readValue(message.getContent(), ChangeGameRequest.class);
            String gameId = request.getGameId().toUpperCase();
            Game game = GameTypeController.getGame(gameId);

            if (game == null
                    || !game.getHost().equals(message.getSender())
                    || game.getGameState() != GameState.FINISHED) {
                sendError(message.getSender(), "Cannot change game");
                return;
            }

            Optional<GameType> gameType = gameTypeRegistry.getGameTypeById(request.getGameTypeId());
            if (gameType.isEmpty()) {
                sendError(message.getSender(), "Invalid game type");
                return;
            }

            Optional<GameStrategy> strategyOpt =
                    strategyRegistry.getStrategy(request.getGameTypeId());
            if (strategyOpt.isEmpty()) {
                sendError(message.getSender(), "No strategy found for game type");
                return;
            }

            // Update game type and config
            game.setGameTypeId(request.getGameTypeId());
            game.setGameTypeName(gameType.get().getName());
            game.setTotalRounds(request.getTotalRounds() > 0 ? request.getTotalRounds() : 3);
            game.setGameState(GameState.LOBBY);
            game.setCurrentRoundNumber(0);
            game.setCurrentRound(null);
            game.setCurrentRoundInfo(null);

            // Create new config for the new game type
            BaseGameConfig config = strategyOpt.get().createDefaultConfig();
            int roundDuration = request.getRoundDuration() > 0 ? request.getRoundDuration() : 30;
            config.setRoundDuration(roundDuration);

            // Apply game-specific settings
            switch (config) {
                case WordScrambleConfig wsConfig ->
                        wsConfig.setWordLength(
                                request.getWordLength() > 0 ? request.getWordLength() : 7);
                case NumberGuessConfig ngConfig -> {
                    ngConfig.setMinRange(request.getMinRange() > 0 ? request.getMinRange() : 1);
                    ngConfig.setMaxRange(request.getMaxRange() > 0 ? request.getMaxRange() : 100);
                }
                case PictionaryConfig pConfig -> pConfig.setTotalTurns(game.getPlayers().size());
                case SlidingPuzzleConfig spConfig -> {
                    var diff = parseDifficulty(request.getDifficulty());
                    spConfig.setDifficulty(diff);
                    spConfig.setGridSize(getGridSizeForDifficulty(diff));
                    config.setRoundDuration(getDurationForDifficulty(diff, roundDuration));
                }
                case SpeedTypingConfig stConfig -> {
                    var diff = parseSpeedTypingDifficulty(request.getDifficulty());
                    stConfig.setDifficulty(diff);
                }
                case ReactionShowdownConfig rsConfig ->
                        rsConfig.setIncludeFakeOuts(request.isIncludeFakeOuts());
                default -> {}
            }

            game.setGameConfig(config);

            // Reset player scores
            for (Player player : game.getPlayers()) {
                player.setScore(0);
            }

            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            log.info(
                    "Game {} changed to {} by host {}",
                    gameId,
                    request.getGameTypeId(),
                    message.getSender());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse change game request", e);
            sendError(message.getSender(), "Invalid request format");
        }
    }

    // ==================== Player Management ====================

    @MessageMapping("/leave")
    public void leaveGame(GameMessage message) {
        String gameId = message.getContent().toUpperCase();
        Game game = GameTypeController.getGame(gameId);

        if (game == null) {
            return;
        }

        String playerName = message.getSender();
        boolean isHost = game.getHost().equals(playerName);

        if (isHost) {
            roundManager.cancelRoundTimer(gameId);
            GameTypeController.removeGame(gameId);

            Map<String, Object> hostLeftMessage = new HashMap<>();
            hostLeftMessage.put("type", "HOST_LEFT");
            hostLeftMessage.put("message", "The host has left the game. Game terminated.");
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/terminated", hostLeftMessage);

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

        Game game = GameTypeController.getGame(gameId);
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
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/removed/" + playerToRemove,
                    Map.of(
                            "type",
                            "REMOVED",
                            "message",
                            "You have been removed from the game by the host."));
            messagingTemplate.convertAndSend("/topic/game/" + gameId, game);
            log.info("Host {} removed player {} from game {}", hostName, playerToRemove, gameId);
        }
    }

    // ==================== Utility Methods ====================

    private void sendError(String username, String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        messagingTemplate.convertAndSend("/topic/error/" + username, error);
    }

    private SlidingPuzzleConfig.Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return SlidingPuzzleConfig.Difficulty.MEDIUM;
        }
        try {
            return SlidingPuzzleConfig.Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SlidingPuzzleConfig.Difficulty.MEDIUM;
        }
    }

    private SpeedTypingConfig.Difficulty parseSpeedTypingDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return SpeedTypingConfig.Difficulty.MEDIUM;
        }
        try {
            return SpeedTypingConfig.Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SpeedTypingConfig.Difficulty.MEDIUM;
        }
    }

    private int getGridSizeForDifficulty(SlidingPuzzleConfig.Difficulty diff) {
        return switch (diff) {
            case EASY -> 3;
            case MEDIUM -> 4;
            case HARD -> 5;
        };
    }

    private int getDurationForDifficulty(SlidingPuzzleConfig.Difficulty diff, int defaultDuration) {
        // Use provided duration or calculate from difficulty
        if (defaultDuration > 0) {
            return defaultDuration;
        }
        return switch (diff) {
            case EASY -> 60;
            case MEDIUM -> 90;
            case HARD -> 120;
        };
    }
}
