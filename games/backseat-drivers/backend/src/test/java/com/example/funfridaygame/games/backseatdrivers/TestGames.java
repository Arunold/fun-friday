package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared set-up for the Backseat Drivers scenario tests: tiny hand-made maps and shortcuts. */
final class TestGames {

    /**
     * A 3-lane road to the east with grass verges. The bus starts at (5, 3); the CEO's car stands
     * at (2..3, 3), one free cell behind it. Parking cells sit at both ends of the bottom lane (so
     * test maps that block the road stay valid). Hazards are added per test with {@link #strip}.
     */
    static final String[] STRIP = {
        "##################################################",
        "#................................................#",
        "#rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr#",
        "#rEErSrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr#",
        "#PrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrP#",
        "#................................................#",
        "##################################################"
    };

    private TestGames() {}

    /** Parses a map with an east-facing start. */
    static TrackMap map(String name, String... rows) {
        return TrackLoader.parse(
                name, "name: " + name + "\nheading: E\n---\n" + String.join("\n", rows));
    }

    /**
     * The {@link #STRIP} with characters replaced on the bus's row ({@code y = 3}).
     *
     * @param hazards column → character, e.g. {@code Map.of(9, 'C')}
     */
    static TrackMap strip(Map<Integer, Character> hazards) {
        String[] rows = STRIP.clone();
        char[] row = rows[3].toCharArray();
        hazards.forEach((x, symbol) -> row[x] = symbol);
        rows[3] = new String(row);
        return map("Strip", rows);
    }

    /**
     * The {@link #STRIP} with whole columns of all three lanes replaced, e.g. rain or a geese
     * crossing. Next to a geese crossing the verges become trees, so the geese block the road.
     *
     * @param columns column → character
     */
    static TrackMap lanes(Map<Integer, Character> columns) {
        char[][] grid = new char[STRIP.length][];
        for (int y = 0; y < STRIP.length; y++) {
            grid[y] = STRIP[y].toCharArray();
        }
        columns.forEach(
                (x, symbol) -> {
                    for (int y = 2; y <= 4; y++) {
                        grid[y][x] = symbol;
                    }
                    if (symbol == 'G') {
                        grid[1][x] = 'T';
                        grid[5][x] = 'T';
                    }
                });
        String[] rows = new String[grid.length];
        for (int y = 0; y < grid.length; y++) {
            rows[y] = new String(grid[y]);
        }
        return map("Lanes", rows);
    }

    /** A game on custom maps (one round per map unless {@code settings} say otherwise). */
    static GameHarness start(List<TrackMap> maps, Map<String, ?> settings, String... players) {
        GameDefinition definition =
                new BackseatDriversGame() {
                    @Override
                    public GameLogic createLogic(GameSettings values) {
                        return new BackseatDriversLogic(Rules.from(values), maps);
                    }
                };
        Map<String, Object> values = new HashMap<>();
        values.put("rounds", Math.min(3, maps.size()));
        values.putAll(settings);
        return GameHarness.of(definition).players(players).settings(values).seed(11).start();
    }

    /** A one-level game on one map with three players. */
    static GameHarness start(TrackMap map) {
        return start(List.of(map), Map.of(), "ann", "bob", "cleo");
    }

    /** The roles per player id from the debug state. */
    @SuppressWarnings("unchecked")
    static Map<String, List<String>> roles(GameHarness game) {
        return (Map<String, List<String>>) game.debugState().get("roles");
    }

    /** The (first) holder of a control. */
    static String holder(GameHarness game, Control control) {
        return holders(game, control).getFirst();
    }

    /** Everyone holding a control. */
    static List<String> holders(GameHarness game, Control control) {
        List<String> holders = new ArrayList<>();
        roles(game)
                .forEach(
                        (id, controls) -> {
                            if (controls.contains(control.name())) {
                                holders.add(id);
                            }
                        });
        return holders;
    }

    /** The navigator's id. */
    static String navigator(GameHarness game) {
        return (String) game.debugState().get("navigator");
    }

    /** Presses or releases a control as its holder. */
    static GameHarness press(GameHarness game, Control control, boolean pressed) {
        return game.act(holder(game, control), "input", input(control, pressed));
    }

    /** Taps a utility control as its holder. */
    static GameHarness tap(GameHarness game, Control control) {
        press(game, control, true);
        return press(game, control, false);
    }

    /** An input payload. */
    static Map<String, Object> input(Control control, boolean pressed) {
        return Map.of("control", control.name(), "pressed", pressed);
    }

    /** The bus from the debug state. */
    @SuppressWarnings("unchecked")
    static Map<String, Double> bus(GameHarness game) {
        return (Map<String, Double>) game.debugState().get("bus");
    }

    /** The level part of a view. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> level(Map<String, Object> view) {
        return (Map<String, Object>) view.get("level");
    }

    /** The vote part of a view. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> vote(Map<String, Object> view) {
        return (Map<String, Object>) view.get("vote");
    }
}
