package com.example.funfridaygame.catalog;

import com.example.funfridaygame.room.ErrorCode;
import com.example.funfridaygame.room.RoomException;
import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.SettingsResolver;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Every installed game module, discovered through {@code @FunFridayGame}. */
@Service
public class GameCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(GameCatalog.class);

    private final Map<String, GameDefinition> games = new LinkedHashMap<>();

    public GameCatalog(List<GameDefinition> definitions) {
        definitions.stream()
                .sorted(Comparator.comparing(d -> d.info().name()))
                .forEach(this::register);
        LOG.info("Loaded {} games: {}", games.size(), games.keySet());
    }

    private void register(GameDefinition definition) {
        String id = definition.info().id();
        if (games.containsKey(id)) {
            throw new IllegalStateException("Two games use the id " + id);
        }
        Set<String> keys = new HashSet<>();
        for (SettingDef setting : definition.settings()) {
            if (!keys.add(setting.key())) {
                throw new IllegalStateException(
                        id + " declares setting " + setting.key() + " twice");
            }
        }
        SettingsResolver.resolve(definition, Map.of()); // defaults must be valid
        games.put(id, definition);
    }

    /** All games, sorted by name. */
    public List<GameDefinition> all() {
        return List.copyOf(games.values());
    }

    public Optional<GameDefinition> find(String id) {
        return Optional.ofNullable(id == null ? null : games.get(id));
    }

    /**
     * A game by id.
     *
     * @throws RoomException with {@link ErrorCode#UNKNOWN_GAME}
     */
    public GameDefinition require(String id) {
        return find(id).orElseThrow(
                        () -> new RoomException(ErrorCode.UNKNOWN_GAME, "Unknown game: " + id));
    }

    /**
     * Validates submitted settings for a game.
     *
     * @throws RoomException with {@link ErrorCode#INVALID_SETTINGS}
     */
    public GameSettings resolve(String gameId, Map<String, ?> submitted) {
        try {
            return SettingsResolver.resolve(require(gameId), submitted);
        } catch (InvalidSettingsException e) {
            throw new RoomException(ErrorCode.INVALID_SETTINGS, e.getMessage());
        }
    }
}
