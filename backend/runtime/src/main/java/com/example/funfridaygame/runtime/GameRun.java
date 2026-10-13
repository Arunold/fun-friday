package com.example.funfridaygame.runtime;

import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Drives one game run: rounds → turns → reveal → … → podium.
 *
 * <p>Not thread-safe. The owner serializes all calls, including the tasks it runs for the {@link
 * TimerService}. Every timer carries the token of the turn that created it, so a late timer can
 * never affect a later turn.
 *
 * <p>Robustness: an exception thrown by game code is logged and contained — a crash while starting
 * a turn ends that turn, a crash while scoring scores nothing — so a buggy game never freezes a
 * room.
 */
public final class GameRun {

    private static final System.Logger LOG = System.getLogger(GameRun.class.getName());

    private final String roomCode;
    private final String gameId;
    private final GameSettings settings;
    private final GameLogic logic;
    private final RandomGenerator random;
    private final InstantSource clock;
    private final TimerService timers;
    private final ActionConverter converter;
    private final GameRunConfig config;
    private final GameRunListener listener;
    private final Context ctx = new Context();

    private final Map<String, Seat> seats = new LinkedHashMap<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final List<TurnRecord> history = new ArrayList<>();
    private final Map<String, TimerService.Cancellable> gameTimers = new HashMap<>();

    private RunPhase phase;
    private int round;
    private int turn;
    private int turnsInRound;
    private long turnStartedAt;
    private long deadline;
    private long deadlineSetAt;
    private long revealStartedAt;
    private long revealEndsAt;
    private long turnToken;
    private boolean endRequested;
    private boolean updateSuppressed;
    private TimerService.Cancellable deadlineTimer;
    private TimerService.Cancellable revealTimer;
    private TurnRecord lastTurn;
    private GameResult result;
    private Instant startedAt;

    GameRun(GameRunBuilder builder) {
        this.roomCode = Objects.requireNonNull(builder.roomCode, "roomCode");
        this.gameId = Objects.requireNonNull(builder.gameId, "gameId");
        this.settings = Objects.requireNonNull(builder.settings, "settings");
        this.logic = Objects.requireNonNull(builder.logic, "logic");
        this.random = Objects.requireNonNull(builder.random, "random");
        this.clock = Objects.requireNonNull(builder.clock, "clock");
        this.timers = Objects.requireNonNull(builder.timers, "timers");
        this.converter = Objects.requireNonNull(builder.converter, "converter");
        this.config = Objects.requireNonNull(builder.config, "config");
        this.listener = Objects.requireNonNull(builder.listener, "listener");
        for (PlayerInfo player : builder.players) {
            seats.put(player.id(), new Seat(player));
            scores.put(player.id(), 0);
        }
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("A game needs players");
        }
    }

    /** Starts building a run. */
    public static GameRunBuilder builder() {
        return new GameRunBuilder();
    }

    // ------------------------------------------------------------------ commands

    /** Starts round 1. */
    public void start() {
        if (phase != null) {
            throw new IllegalStateException("Game already started");
        }
        startedAt = clock.instant();
        round = 1;
        turn = 1;
        turnsInRound = turnsOf();
        beginTurnOrAdvance();
    }

    /**
     * Forwards a player's action to the game.
     *
     * @throws InvalidActionException when the action is rejected
     */
    public void act(String playerId, String type, Object payload) {
        if (phase != RunPhase.PLAYING) {
            throw new InvalidActionException("Wait for the next turn");
        }
        Seat seat = seats.get(playerId);
        if (seat == null || seat.left) {
            throw new InvalidActionException("You are not playing in this game");
        }
        if (type == null || type.isBlank()) {
            throw new InvalidActionException("Missing action type");
        }
        updateSuppressed = false;
        try {
            logic.onAction(ctx, seat.info, new RunAction(type, payload));
        } catch (InvalidActionException e) {
            throw e;
        } catch (RuntimeException e) {
            log(e, "onAction(" + type + ")");
            throw new InvalidActionException("That move didn't work, please try again");
        }
        if (updateSuppressed && !endRequested) {
            updateSuppressed = false;
            checkTurnEnd();
            return;
        }
        afterChange(false);
    }

    /** Marks a player as (dis)connected; disconnected players don't hold up turn completion. */
    public void setConnected(String playerId, boolean connected) {
        Seat seat = seats.get(playerId);
        if (seat == null || seat.left || seat.connected == connected) {
            return;
        }
        seat.connected = connected;
        checkTurnEnd();
    }

    /** Removes a player for good (left, kicked or timed out). */
    public void removePlayer(String playerId) {
        Seat seat = seats.get(playerId);
        if (seat == null || seat.left) {
            return;
        }
        seat.left = true;
        cancelTimersOnlyIfNobodyLeft();
        if (phase == RunPhase.PLAYING || phase == RunPhase.REVEAL) {
            invoke("onPlayerLeft", () -> logic.onPlayerLeft(ctx, seat.info));
        }
        if (phase == RunPhase.PLAYING) {
            afterChange(true);
        }
    }

    /** Stops all timers (the room was closed). */
    public void abort() {
        cancelAllTimers();
        phase = RunPhase.FINISHED;
    }

    // ------------------------------------------------------------------ queries

    /** What a viewer sees, or {@code null} before the start and after the end. */
    public Object view(Viewer viewer) {
        if (phase == null || phase == RunPhase.FINISHED) {
            return null;
        }
        return safe("view", () -> logic.view(ctx, viewer), null);
    }

    /** Game-provided state for automated tests. */
    public Object debugState() {
        return safe("debugState", () -> logic.debugState(ctx), null);
    }

    public String gameId() {
        return gameId;
    }

    public GameSettings settings() {
        return settings;
    }

    /** Current phase, {@code null} before {@link #start()}. */
    public RunPhase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int totalRounds() {
        return Math.max(1, settings.rounds());
    }

    public int turn() {
        return turn;
    }

    public int turnsInRound() {
        return turnsInRound;
    }

    /** When the current phase ends: the turn deadline or the end of the reveal (epoch ms). */
    public long phaseEndsAt() {
        return phase == RunPhase.REVEAL ? revealEndsAt : deadline;
    }

    /**
     * When the current timed phase began: the turn start (or the last {@link
     * GameContext#setDeadline} call) or the start of the reveal (epoch ms). Lets clients draw
     * progress rings.
     */
    public long phaseStartedAt() {
        return phase == RunPhase.REVEAL ? revealStartedAt : deadlineSetAt;
    }

    public long turnStartedAt() {
        return turnStartedAt;
    }

    /** Total score per player still in the game. */
    public Map<String, Integer> scores() {
        Map<String, Integer> copy = new LinkedHashMap<>();
        seats.forEach(
                (id, seat) -> {
                    if (!seat.left) {
                        copy.put(id, scores.getOrDefault(id, 0));
                    }
                });
        return Collections.unmodifiableMap(copy);
    }

    /** Every finished turn so far. */
    public List<TurnRecord> history() {
        return List.copyOf(history);
    }

    /** The turn shown on the reveal screen (or the last finished one). */
    public TurnRecord lastTurn() {
        return lastTurn;
    }

    /** The final result once {@link #phase()} is {@link RunPhase#FINISHED}. */
    public GameResult result() {
        return result;
    }

    /** Players still in the game, in join order. */
    public List<PlayerInfo> players() {
        List<PlayerInfo> list = new ArrayList<>();
        for (Seat seat : seats.values()) {
            if (!seat.left) {
                list.add(seat.info);
            }
        }
        return list;
    }

    /** Whether the player takes part in this game (and hasn't left). */
    public boolean isPlaying(String playerId) {
        Seat seat = seats.get(playerId);
        return seat != null && !seat.left;
    }

    // ------------------------------------------------------------------ turn loop

    private int turnsOf() {
        return Math.max(0, safe("turnsInRound", () -> logic.turnsInRound(ctx), 1));
    }

    /** Starts the current turn, skipping turns the game doesn't want, or finishes the game. */
    private void beginTurnOrAdvance() {
        while (true) {
            if (turn > turnsInRound) {
                if (round >= totalRounds()) {
                    finish();
                    return;
                }
                round++;
                turn = 1;
                turnsInRound = turnsOf();
                continue;
            }
            if (!safe("skipTurn", () -> logic.skipTurn(ctx), false)) {
                break;
            }
            turn++;
        }
        beginTurn();
    }

    private void beginTurn() {
        phase = RunPhase.PLAYING;
        turnToken++;
        endRequested = false;
        cancelGameTimers();
        turnStartedAt = now();
        deadlineSetAt = turnStartedAt;
        deadline = turnStartedAt + defaultTurnMillis();
        if (!invoke("startTurn", () -> logic.startTurn(ctx))) {
            endRequested = true;
        }
        scheduleDeadline();
        listener.changed(true);
        checkTurnEnd();
    }

    private void afterChange(boolean urgent) {
        if (phase != RunPhase.PLAYING) {
            return;
        }
        if (endRequested || safe("isTurnComplete", () -> logic.isTurnComplete(ctx), false)) {
            endTurnNow();
        } else {
            listener.changed(urgent);
        }
    }

    private void checkTurnEnd() {
        if (phase == RunPhase.PLAYING
                && (endRequested
                        || safe("isTurnComplete", () -> logic.isTurnComplete(ctx), false))) {
            endTurnNow();
        }
    }

    private void endTurnNow() {
        if (phase != RunPhase.PLAYING) {
            return;
        }
        phase = RunPhase.REVEAL;
        cancelDeadline();
        cancelGameTimers();
        TurnOutcome outcome =
                safe("endTurn", () -> logic.endTurn(ctx), TurnOutcome.builder().build());
        Map<String, Integer> earned = new LinkedHashMap<>();
        if (outcome != null) {
            outcome.points()
                    .forEach(
                            (playerId, points) -> {
                                Seat seat = seats.get(playerId);
                                if (seat != null && !seat.left) {
                                    earned.put(playerId, points);
                                    int total = scores.getOrDefault(playerId, 0) + points;
                                    scores.put(playerId, Math.max(0, total));
                                }
                            });
        }
        lastTurn = new TurnRecord(round, turn, earned, outcome == null ? "" : outcome.headline());
        history.add(lastTurn);
        revealStartedAt = now();
        revealEndsAt = revealStartedAt + config.revealDuration().toMillis();
        long token = turnToken;
        revealTimer = timers.schedule(config.revealDuration(), () -> onRevealOver(token));
        listener.changed(true);
    }

    private void onRevealOver(long token) {
        if (token != turnToken || phase != RunPhase.REVEAL) {
            return;
        }
        revealTimer = null;
        turn++;
        beginTurnOrAdvance();
    }

    private void finish() {
        cancelAllTimers();
        phase = RunPhase.FINISHED;
        List<PlayerInfo> remaining = players();
        List<GameResult.Standing> standings = GameResult.rank(remaining, scores);
        List<Award> awards = new ArrayList<>();
        for (Award award : safe("awards", () -> logic.awards(ctx), List.<Award>of())) {
            if (award != null && isPlaying(award.playerId())) {
                awards.add(award);
            }
        }
        result = new GameResult(standings, awards, history, startedAt, clock.instant());
        listener.finished(result);
        listener.changed(true);
    }

    // ------------------------------------------------------------------ timers

    private long defaultTurnMillis() {
        return settings.roundDurationSeconds().isPresent()
                ? settings.roundDurationSeconds().getAsInt() * 1000L
                : config.defaultTurnDuration().toMillis();
    }

    private void scheduleDeadline() {
        cancelDeadline();
        long token = turnToken;
        long delay = Math.max(0, deadline - now());
        deadlineTimer = timers.schedule(Duration.ofMillis(delay), () -> onDeadline(token));
    }

    private void onDeadline(long token) {
        if (token == turnToken && phase == RunPhase.PLAYING) {
            deadlineTimer = null;
            endTurnNow();
        }
    }

    private void onGameTimer(long token, String key) {
        if (token != turnToken || phase != RunPhase.PLAYING) {
            return;
        }
        gameTimers.remove(key);
        updateSuppressed = false;
        invoke("onTimer(" + key + ")", () -> logic.onTimer(ctx, key));
        if (updateSuppressed && !endRequested) {
            updateSuppressed = false;
            checkTurnEnd();
            return;
        }
        afterChange(true);
    }

    private void cancelDeadline() {
        if (deadlineTimer != null) {
            deadlineTimer.cancel();
            deadlineTimer = null;
        }
    }

    private void cancelGameTimers() {
        gameTimers.values().forEach(TimerService.Cancellable::cancel);
        gameTimers.clear();
    }

    private void cancelAllTimers() {
        cancelDeadline();
        cancelGameTimers();
        if (revealTimer != null) {
            revealTimer.cancel();
            revealTimer = null;
        }
    }

    private void cancelTimersOnlyIfNobodyLeft() {
        if (players().isEmpty()) {
            cancelAllTimers();
        }
    }

    private long now() {
        return clock.millis();
    }

    // ------------------------------------------------------------------ error containment

    private <T> T safe(String what, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            log(e, what);
            return fallback;
        }
    }

    private boolean invoke(String what, Runnable call) {
        try {
            call.run();
            return true;
        } catch (RuntimeException e) {
            log(e, what);
            return false;
        }
    }

    private void log(RuntimeException e, String what) {
        LOG.log(
                Level.ERROR,
                () -> "Game '" + gameId + "' in room " + roomCode + " failed in " + what,
                e);
    }

    // ------------------------------------------------------------------ helpers

    private static final class Seat {
        private final PlayerInfo info;
        private boolean connected = true;
        private boolean left;

        private Seat(PlayerInfo info) {
            this.info = info;
        }
    }

    private final class RunAction implements Action {
        private final String type;
        private final Object payload;

        private RunAction(String type, Object payload) {
            this.type = type;
            this.payload = payload;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public <T> T payload(Class<T> target) {
            return converter.convert(payload, target);
        }
    }

    private final class Context implements GameContext {

        @Override
        public String roomCode() {
            return roomCode;
        }

        @Override
        public GameSettings settings() {
            return settings;
        }

        @Override
        public int round() {
            return round;
        }

        @Override
        public int totalRounds() {
            return GameRun.this.totalRounds();
        }

        @Override
        public int turn() {
            return turn;
        }

        @Override
        public int turnsInRound() {
            return turnsInRound;
        }

        @Override
        public List<PlayerInfo> players() {
            return GameRun.this.players();
        }

        @Override
        public List<PlayerInfo> activePlayers() {
            List<PlayerInfo> list = new ArrayList<>();
            for (Seat seat : seats.values()) {
                if (!seat.left && seat.connected) {
                    list.add(seat.info);
                }
            }
            return list;
        }

        @Override
        public Optional<PlayerInfo> player(String playerId) {
            Seat seat = playerId == null ? null : seats.get(playerId);
            return seat == null || seat.left ? Optional.empty() : Optional.of(seat.info);
        }

        @Override
        public boolean isActive(String playerId) {
            Seat seat = playerId == null ? null : seats.get(playerId);
            return seat != null && !seat.left && seat.connected;
        }

        @Override
        public Map<String, Integer> scores() {
            return GameRun.this.scores();
        }

        @Override
        public RandomGenerator random() {
            return random;
        }

        @Override
        public long now() {
            return GameRun.this.now();
        }

        @Override
        public long turnStartedAt() {
            return turnStartedAt;
        }

        @Override
        public long deadline() {
            return deadline;
        }

        @Override
        public void setDeadline(Duration fromNow) {
            deadlineSetAt = GameRun.this.now();
            deadline = deadlineSetAt + Math.max(0, fromNow.toMillis());
            if (phase == RunPhase.PLAYING && deadlineTimer != null) {
                scheduleDeadline();
            }
        }

        @Override
        public void extendDeadline(Duration delta) {
            deadline = Math.max(GameRun.this.now(), deadline + delta.toMillis());
            if (phase == RunPhase.PLAYING && deadlineTimer != null) {
                scheduleDeadline();
            }
        }

        @Override
        public void schedule(String key, Duration delay) {
            cancel(key);
            long token = turnToken;
            gameTimers.put(key, timers.schedule(delay, () -> onGameTimer(token, key)));
        }

        @Override
        public void cancel(String key) {
            TimerService.Cancellable existing = gameTimers.remove(key);
            if (existing != null) {
                existing.cancel();
            }
        }

        @Override
        public void endTurn() {
            endRequested = true;
        }

        @Override
        public void suppressUpdate() {
            updateSuppressed = true;
        }

        @Override
        public void stream(String type, Object payload) {
            listener.stream(type, payload);
        }

        @Override
        public void sendTo(String playerId, String type, Object payload) {
            if (isPlaying(playerId)) {
                listener.sendTo(playerId, type, payload);
            }
        }
    }
}
