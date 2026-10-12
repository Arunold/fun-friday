package com.example.funfridaygame.sdk;

import java.util.List;

/**
 * Describes a game and creates its logic. One instance per game type (a Spring singleton).
 *
 * <p>Implementations must be stateless; all per-game state belongs in the {@link GameLogic}
 * returned by {@link #createLogic(GameSettings)}.
 */
public interface GameDefinition {

    /** Metadata shown in the game catalog. */
    GameInfo info();

    /**
     * The settings the host can change. Include {@link CommonSettings#rounds} (the platform reads
     * {@value GameSettings#ROUNDS}) and, if turns have a fixed length, {@link
     * CommonSettings#roundDuration}.
     */
    List<SettingDef> settings();

    /**
     * Cross-field validation after every value has been checked against its {@link SettingDef}.
     *
     * @return human-readable problems; empty when the settings are valid
     */
    default List<String> validate(GameSettings settings) {
        return List.of();
    }

    /** Creates the logic for one game run (from the first round to the podium). */
    GameLogic createLogic(GameSettings settings);
}
