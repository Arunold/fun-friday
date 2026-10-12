package com.example.funfridaygame.testkit;

import com.example.funfridaygame.runtime.GameResult;
import com.example.funfridaygame.runtime.GameRun;
import com.example.funfridaygame.runtime.GameRunConfig;
import com.example.funfridaygame.runtime.GameRunListener;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.runtime.SettingsResolver;
import com.example.funfridaygame.runtime.TurnRecord;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.Viewer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays a game module in a test: real {@link GameRun} engine, virtual time, no Spring, no network.
 *
 * <pre>{@code
 * GameHarness game = GameHarness.of(new NumberGuessGame())
 *         .players("alice", "bob")
 *         .settings(Map.of("rounds", 1))
 *         .seed(7)
 *         .start();
 * game.act("alice", "guess", Map.of("value", 50));
 * game.finishTurn();
 * assertThat(game.lastTurn().points()).containsKey("alice");
 * }</pre>
 *
 * <p>Player ids are the names passed to {@link #players(String...)}.
 */
public final class GameHarness {

    private static final String[] COLORS = {
        "#1ed760", "#ff6b6b", "#4dabf7", "#ffd43b", "#cc5de8", "#ff922b", "#20c997", "#f06595"
    };

    private final GameDefinition definition;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ManualTime time = new ManualTime();
    private final List<PlayerInfo> players = new ArrayList<>();
    private final Map<String, Object> settingOverrides = new LinkedHashMap<>();
    private final List<Event> streamed = new ArrayList<>();
    private final Map<String, List<Event>> privateEvents = new LinkedHashMap<>();
    private long seed = 1;
    private Duration reveal = Duration.ofSeconds(5);
    private GameRun run;
    private int changes;

    private GameHarness(GameDefinition definition) {
        this.definition = definition;
    }

    /** A harness for one game definition. */
    public static GameHarness of(GameDefinition definition) {
        return new GameHarness(definition);
    }

    /** An event captured from {@code stream} or {@code sendTo}. */
    public record Event(String type, Object payload) {}

    // ------------------------------------------------------------------ setup

    /** Adds players; their names are also their ids. */
    public GameHarness players(String... names) {
        for (String name : names) {
            players.add(new PlayerInfo(name, name, "🙂", COLORS[players.size() % COLORS.length]));
        }
        return this;
    }

    /** Overrides settings (validated exactly like the platform does). */
    public GameHarness settings(Map<String, ?> overrides) {
        settingOverrides.putAll(overrides);
        return this;
    }

    /** Seeds the game's random generator. */
    public GameHarness seed(long value) {
        this.seed = value;
        return this;
    }

    /** Length of the reveal pause (default 5 s). */
    public GameHarness revealDuration(Duration value) {
        this.reveal = value;
        return this;
    }

    /** Validates the settings, creates the logic and starts round 1. */
    public GameHarness start() {
        GameSettings settings = SettingsResolver.resolve(definition, settingOverrides);
        run =
                GameRun.builder()
                        .roomCode("TEST")
                        .gameId(definition.info().id())
                        .settings(settings)
                        .logic(definition.createLogic(settings))
                        .players(players)
                        .random(new Random(seed))
                        .clock(time)
                        .timers(time)
                        .converter(new JsonActionConverter(mapper))
                        .config(new GameRunConfig(reveal, Duration.ofSeconds(60)))
                        .listener(new Listener())
                        .build();
        run.start();
        return this;
    }

    // ------------------------------------------------------------------ playing

    /** Sends an action; {@code payload} may be a map, a record or {@code null}. */
    public GameHarness act(String playerId, String type, Object payload) {
        run.act(playerId, type, payload);
        return this;
    }

    /** Sends an action that must be rejected and returns the rejection. */
    public InvalidActionException reject(String playerId, String type, Object payload) {
        try {
            run.act(playerId, type, payload);
        } catch (InvalidActionException e) {
            return e;
        }
        throw new AssertionError("Expected '" + type + "' from " + playerId + " to be rejected");
    }

    /** Moves virtual time forward, firing due timers. */
    public GameHarness advance(Duration duration) {
        time.advanceTo(time.millis() + duration.toMillis());
        return this;
    }

    /** Moves virtual time forward by (fractional) seconds. */
    public GameHarness advanceSeconds(double seconds) {
        return advance(Duration.ofMillis(Math.round(seconds * 1000)));
    }

    /** Runs timers until the current turn is over (deadline or completion). */
    public GameHarness finishTurn() {
        while (run.phase() == RunPhase.PLAYING) {
            if (!time.runNext()) {
                throw new AssertionError("Turn can't end: no pending timers");
            }
        }
        return this;
    }

    /** Runs timers through the reveal until the next turn starts or the game ends. */
    public GameHarness nextTurn() {
        while (run.phase() == RunPhase.REVEAL) {
            if (!time.runNext()) {
                throw new AssertionError("Reveal can't end: no pending timers");
            }
        }
        return this;
    }

    /** Plays out every remaining turn without further actions. */
    public GameHarness finishGame() {
        while (run.phase() != RunPhase.FINISHED) {
            if (!time.runNext()) {
                throw new AssertionError("Game can't finish: no pending timers");
            }
        }
        return this;
    }

    /** Simulates a lost connection. */
    public GameHarness disconnect(String playerId) {
        run.setConnected(playerId, false);
        return this;
    }

    /** Simulates a reconnect. */
    public GameHarness reconnect(String playerId) {
        run.setConnected(playerId, true);
        return this;
    }

    /** Removes a player for good. */
    public GameHarness leave(String playerId) {
        run.removePlayer(playerId);
        return this;
    }

    // ------------------------------------------------------------------ inspecting

    /** The view of a player as a JSON-like map (what the browser receives). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> view(String playerId) {
        Object view = run.view(Viewer.player(playerId));
        return view == null ? null : mapper.convertValue(view, Map.class);
    }

    /** The view of a player converted to {@code type}. */
    public <T> T view(String playerId, Class<T> type) {
        Object view = run.view(Viewer.player(playerId));
        return view == null ? null : mapper.convertValue(view, type);
    }

    /** The spectator view as a JSON-like map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> spectatorView() {
        Object view = run.view(Viewer.spectator());
        return view == null ? null : mapper.convertValue(view, Map.class);
    }

    /** Game-provided debug state as a JSON-like map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> debugState() {
        Object state = run.debugState();
        return state == null ? null : mapper.convertValue(state, Map.class);
    }

    public RunPhase phase() {
        return run.phase();
    }

    public int round() {
        return run.round();
    }

    public int turn() {
        return run.turn();
    }

    public Map<String, Integer> scores() {
        return run.scores();
    }

    /** Total score of one player. */
    public int score(String playerId) {
        return run.scores().getOrDefault(playerId, 0);
    }

    public TurnRecord lastTurn() {
        return run.lastTurn();
    }

    public GameResult result() {
        return run.result();
    }

    /** Public events sent with {@code ctx.stream}. */
    public List<Event> streamed() {
        return List.copyOf(streamed);
    }

    /** Private events sent to a player with {@code ctx.sendTo}. */
    public List<Event> privateEvents(String playerId) {
        return List.copyOf(privateEvents.getOrDefault(playerId, List.of()));
    }

    /** Current virtual time in epoch milliseconds. */
    public long now() {
        return time.millis();
    }

    /** Milliseconds until the current turn's deadline (or the end of the reveal). */
    public long remainingMillis() {
        return Math.max(0, run.phaseEndsAt() - time.millis());
    }

    /** How many state changes the engine announced (useful to check broadcasting). */
    public int changes() {
        return changes;
    }

    /** The underlying engine. */
    public GameRun run() {
        return run;
    }

    private final class Listener implements GameRunListener {
        @Override
        public void changed(boolean urgent) {
            changes++;
        }

        @Override
        public void stream(String type, Object payload) {
            streamed.add(new Event(type, payload));
        }

        @Override
        public void sendTo(String playerId, String type, Object payload) {
            privateEvents
                    .computeIfAbsent(playerId, k -> new ArrayList<>())
                    .add(new Event(type, payload));
        }

        @Override
        public void finished(GameResult result) {
            // available through result()
        }
    }
}
