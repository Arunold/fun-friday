package com.example.funfridaygame.service.game;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for game strategies. Automatically registers all GameStrategy implementations.
 * Adding a new game type is as simple as creating a new GameStrategy implementation.
 */
@Component
public class GameStrategyRegistry {

    private final Map<String, GameStrategy> strategies;

    public GameStrategyRegistry(List<GameStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(GameStrategy::getGameTypeId, Function.identity()));
    }

    /**
     * Get the strategy for a specific game type
     */
    public Optional<GameStrategy> getStrategy(String gameTypeId) {
        return Optional.ofNullable(strategies.get(gameTypeId));
    }

    /**
     * Get all registered game type IDs
     */
    public List<String> getAllGameTypeIds() {
        return List.copyOf(strategies.keySet());
    }

    /**
     * Check if a game type is registered
     */
    public boolean isRegistered(String gameTypeId) {
        return strategies.containsKey(gameTypeId);
    }
}
