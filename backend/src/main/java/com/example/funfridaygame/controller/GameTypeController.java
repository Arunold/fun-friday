package com.example.funfridaygame.controller;

import com.example.funfridaygame.model.Game;
import com.example.funfridaygame.model.GameType;
import com.example.funfridaygame.service.GameTypeRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameTypeController {

    private final GameTypeRegistry gameTypeRegistry;
    private static final Map<String, Game> GAMES = new ConcurrentHashMap<>();

    public GameTypeController(GameTypeRegistry gameTypeRegistry) {
        this.gameTypeRegistry = gameTypeRegistry;
    }

    // Static method to register games (called from GameController)
    public static void registerGame(String gameId, Game game) {
        GAMES.put(gameId, game);
    }

    public static void removeGame(String gameId) {
        GAMES.remove(gameId);
    }

    public static Game getGame(String gameId) {
        return GAMES.get(gameId);
    }

    @GetMapping("/game-types")
    public List<GameType> getGameTypes() {
        return gameTypeRegistry.getAllGameTypes();
    }

    @GetMapping("/games/{gameId}/status")
    public ResponseEntity<Map<String, Object>> getGameStatus(@PathVariable String gameId) {
        Game game = GAMES.get(gameId.toUpperCase());
        Map<String, Object> response = new HashMap<>();

        if (game == null) {
            response.put("exists", false);
            response.put("message", "Game not found");
            return ResponseEntity.ok(response);
        }

        response.put("exists", true);
        response.put("gameState", game.getGameState().name());
        response.put("gameTypeId", game.getGameTypeId());
        response.put("gameTypeName", game.getGameTypeName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<Game> getGameDetails(@PathVariable String gameId) {
        Game game = GAMES.get(gameId.toUpperCase());

        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(game);
    }
}
