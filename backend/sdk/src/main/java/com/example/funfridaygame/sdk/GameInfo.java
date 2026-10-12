package com.example.funfridaygame.sdk;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Catalog metadata of a game.
 *
 * @param id stable identifier, equal to the folder name in {@code games/} (kebab-case)
 * @param name display name
 * @param description one-sentence pitch
 * @param icon an emoji
 * @param minPlayers players needed to start
 * @param maxPlayers maximum players in a room for this game
 * @param difficulty how hard the game is to play well
 * @param rules short "how to play" bullet points
 */
public record GameInfo(
        String id,
        String name,
        String description,
        String icon,
        int minPlayers,
        int maxPlayers,
        Difficulty difficulty,
        List<String> rules) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    /** How hard a game is to play well. */
    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public GameInfo {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Game id must be kebab-case: " + id);
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(difficulty, "difficulty");
        if (minPlayers < 1 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException("Invalid player range for " + id);
        }
        description = description == null ? "" : description;
        icon = icon == null ? "🎲" : icon;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
