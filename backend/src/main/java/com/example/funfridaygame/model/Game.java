package com.example.funfridaygame.model;

import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.roundinfo.BaseRoundInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core game model that holds all game state.
 * Game-specific configuration is delegated to BaseGameConfig subclasses.
 * Game-specific round data is delegated to BaseGameRound subclasses.
 * This class remains clean and doesn't need modification when adding new games.
 */
@Getter
@Setter
public class Game {

    // Core game identity
    private String gameId;
    private String host;
    private String gameTypeId;
    private String gameTypeName;

    // Players
    private final List<Player> players = new CopyOnWriteArrayList<>();

    // Game state
    private volatile GameState gameState = GameState.LOBBY;
    private int totalRounds = 3;
    private volatile int currentRoundNumber = 0;

    // Game-specific configuration (polymorphic)
    private BaseGameConfig gameConfig;

    // Current round (internal use only)
    @JsonIgnore
    private volatile BaseGameRound currentRound;

    // Round info sent to clients (polymorphic)
    private volatile BaseRoundInfo currentRoundInfo;

    public void setCurrentRound(BaseGameRound currentRound) {
        this.currentRound = currentRound;
        updateRoundInfo();
    }

    /**
     * Update the round info DTO from current round state.
     * Call this after modifying round state directly (e.g., typing progress updates).
     */
    public void updateCurrentRoundInfo() {
        updateRoundInfo();
    }

    private void updateRoundInfo() {
        if (currentRound != null) {
            boolean includeTarget = gameState == GameState.ROUND_RESULT;
            currentRoundInfo = BaseRoundInfo.fromRound(currentRound, includeTarget, gameConfig);
        }
    }

    public boolean hasMoreRounds() {
        return currentRoundNumber < totalRounds;
    }

    public Player getPlayerByName(String name) {
        return players.stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    public boolean allPlayersAnswered() {
        if (currentRound == null) {
            return false;
        }
        return currentRound.getAnsweredCount() >= players.size();
    }

    /**
     * Get typed game config (for type-safe access in strategies)
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseGameConfig> T getTypedConfig(Class<T> configClass) {
        if (configClass.isInstance(gameConfig)) {
            return (T) gameConfig;
        }
        return null;
    }
}
