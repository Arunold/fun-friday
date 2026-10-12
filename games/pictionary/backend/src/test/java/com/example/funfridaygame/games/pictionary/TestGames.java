package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared set-up for the Pictionary scenario tests. */
final class TestGames {

    /** In "mixed" mode the choices are always [EASY, MEDIUM, HARD] = these words. */
    static final String ELEPHANT = "elephant";

    static final String ICE_CREAM = "ice cream";
    static final String X_RAY = "x-ray";

    private TestGames() {}

    /** Pictionary with the real word lists. */
    static GameHarness classic(Map<String, ?> settings, String... players) {
        return start(new PictionaryGame(), settings, players);
    }

    /**
     * Pictionary whose choices are always {@value #ELEPHANT}, {@value #ICE_CREAM}, {@value #X_RAY}.
     */
    static GameHarness fixed(Map<String, ?> settings, String... players) {
        WordList list =
                new WordList(
                        Map.of(
                                WordList.Level.EASY, List.of(ELEPHANT),
                                WordList.Level.MEDIUM, List.of(ICE_CREAM),
                                WordList.Level.HARD, List.of(X_RAY)));
        GameDefinition definition =
                new PictionaryGame() {
                    @Override
                    public GameLogic createLogic(GameSettings values) {
                        return new PictionaryLogic(
                                Duration.ofSeconds(values.getInt(GameSettings.ROUND_DURATION)),
                                new WordPicker(list, WordPicker.Mode.MIXED),
                                values.getBoolean(PictionaryGame.HINTS));
                    }
                };
        return start(definition, settings, players);
    }

    /** Pictionary with fixed words, one round, the default 80 s timer and hints on. */
    static GameHarness fixed(String... players) {
        return fixed(Map.of(), players);
    }

    private static GameHarness start(
            GameDefinition definition, Map<String, ?> settings, String... players) {
        Map<String, Object> values = new HashMap<>();
        values.put("rounds", 1);
        values.putAll(settings);
        return GameHarness.of(definition).players(players).settings(values).seed(7).start();
    }

    /** The current secret word ({@code null} while choosing). */
    static String word(GameHarness game) {
        return (String) game.debugState().get("word");
    }

    /** The current drawer. */
    static String drawer(GameHarness game) {
        return (String) game.debugState().get("drawerId");
    }

    /** A stroke batch payload with a black 10 px pen. */
    static Map<String, Object> stroke(int id, int... points) {
        return Map.of("id", id, "tool", "pen", "color", "#222222", "size", 10, "points", points);
    }

    /** A fill payload. */
    static Map<String, Object> fill(int id, String color, int x, int y) {
        return Map.of("id", id, "color", color, "x", x, "y", y);
    }

    /** A guess payload. */
    static Map<String, Object> guess(String text) {
        return Map.of("text", text);
    }

    /** The feed of a view. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> feed(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("feed");
    }

    /** The drawing of a view. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> drawing(Map<String, Object> view) {
        return (Map<String, Object>) view.get("drawing");
    }

    /** The visible operations of a view's drawing. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> ops(Map<String, Object> view) {
        return (List<Map<String, Object>>) drawing(view).get("ops");
    }
}
