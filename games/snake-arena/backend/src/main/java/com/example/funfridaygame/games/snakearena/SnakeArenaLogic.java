package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.games.snakearena.SnakeArenaView.ResultRow;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.RosterEntry;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.ShrinkView;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules of Snake Arena. A round is one turn on the round timer, driven by a {@value #TICK_MS} ms
 * tick.
 *
 * <p>{@link #startTurn} builds a fresh {@link Simulation} — arena size from the setting or the
 * player count, snakes on start slots that rotate by one seat per round — and schedules the tick,
 * aligned to the turn start so it doesn't drift. Every tick advances the simulation, streams the
 * compact frame to everybody ({@value #FRAME_EVENT}) and suppresses the snapshot: views only go out
 * when the round starts or ends or the roster changes, and they carry the latest frame so a
 * (re)connecting browser can draw at once. {@value #TURN} actions are queued in the simulation and
 * carried out by the move after the tick they name in {@code at} — the cell where the player's
 * browser shows the snake turning, so network delay never makes a snake turn a cell late or early
 * (without {@code at}: the next move); harmless ones (into the current heading, while crashed) are
 * ignored without an error. In "last snake standing" the round ends as soon as at most one snake is
 * left.
 */
final class SnakeArenaLogic implements GameLogic {

    /** Action: steer ({@code {"dir": "UP", "at": 42}}; {@code at} is optional). */
    static final String TURN = "turn";

    /** Stream event carrying a {@link SnakeArenaView.FrameView} after every tick. */
    static final String FRAME_EVENT = "frame";

    /** Milliseconds per tick. */
    static final long TICK_MS = 100;

    private static final String TICK_TIMER = "tick";

    /**
     * Payload of {@value #TURN}.
     *
     * @param dir the new heading
     * @param at the tick whose head position the turn starts from (optional)
     */
    record TurnPayload(
            @NotNull @Pattern(regexp = "UP|DOWN|LEFT|RIGHT") String dir,
            @PositiveOrZero Integer at) {}

    private final String arenaSetting;
    private final Variation variation;
    private final boolean powerUps;
    private final GameStats stats = new GameStats();
    private Simulation simulation;
    private ArenaSize arena = ArenaSize.SMALL;
    private List<RosterEntry> roster = List.of();
    private long shrinkFrom = -1;
    private long tick;
    private boolean revealed;
    private RoundScoring.Summary summary;

    /**
     * Logic for one game.
     *
     * @param arenaSetting {@value ArenaSize#AUTO} or a size key
     * @param variation the game mode
     * @param powerUps whether power-ups appear
     */
    SnakeArenaLogic(String arenaSetting, Variation variation, boolean powerUps) {
        this.arenaSetting = arenaSetting;
        this.variation = variation;
        this.powerUps = powerUps;
    }

    // ------------------------------------------------------------------ round

    @Override
    public void startTurn(GameContext ctx) {
        List<PlayerInfo> players = ctx.players();
        arena = ArenaSize.resolve(arenaSetting, players.size());
        long turnTicks = (ctx.deadline() - ctx.turnStartedAt()) / TICK_MS;
        shrinkFrom =
                variation == Variation.SHRINKING
                        ? Math.max(
                                Simulation.START_DELAY_TICKS + 1,
                                turnTicks - Simulation.SHRINK_LEAD_TICKS)
                        : -1;
        simulation =
                new Simulation(
                        new Board(arena.cols(), arena.rows()),
                        variation,
                        powerUps,
                        shrinkFrom,
                        ctx.random());
        simulation.start(startOrder(players, ctx.round()));
        roster = players.stream().map(p -> new RosterEntry(p.id(), p.name(), p.color())).toList();
        tick = 0;
        revealed = false;
        summary = null;
        ctx.schedule(TICK_TIMER, Duration.ofMillis(TICK_MS));
    }

    /** Player ids in slot order: join order, rotated by one seat per round. */
    static List<String> startOrder(List<PlayerInfo> players, int round) {
        int count = players.size();
        String[] order = new String[count];
        for (int i = 0; i < count; i++) {
            order[Math.floorMod(i + round - 1, count)] = players.get(i).id();
        }
        return List.of(order);
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!TURN.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        TurnPayload turn = action.payload(TurnPayload.class);
        if (simulation != null && !revealed) {
            simulation.turn(
                    player.id(), Dir.valueOf(turn.dir()), turn.at() == null ? -1 : turn.at());
        }
        ctx.suppressUpdate();
    }

    @Override
    public void onTimer(GameContext ctx, String key) {
        if (!TICK_TIMER.equals(key) || simulation == null || revealed) {
            return;
        }
        tick++;
        simulation.step(tick);
        ctx.stream(FRAME_EVENT, simulation.frame(ctx.round()));
        if (simulation.isOver()) {
            ctx.endTurn();
            return;
        }
        long next = ctx.turnStartedAt() + (tick + 1) * TICK_MS;
        ctx.schedule(TICK_TIMER, Duration.ofMillis(Math.max(1, next - ctx.now())));
        ctx.suppressUpdate();
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        return simulation != null && !revealed && simulation.isOver();
    }

    @Override
    public void onPlayerLeft(GameContext ctx, PlayerInfo player) {
        if (simulation == null || revealed) {
            return;
        }
        simulation.remove(player.id());
        if (simulation.isOver()) {
            ctx.endTurn();
        }
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        if (simulation == null) {
            return TurnOutcome.builder().headline("Time's up!").build();
        }
        summary = RoundScoring.applyBonuses(simulation.snakes(), variation);
        stats.record(simulation.snakes());
        TurnOutcome.Builder outcome = TurnOutcome.builder();
        for (Snake snake : simulation.snakes()) {
            outcome.add(snake.id(), snake.points());
        }
        return outcome.headline(
                        RoundScoring.headline(
                                variation, simulation.snakes(), summary, id -> nameOf(ctx, id)))
                .build();
    }

    // ------------------------------------------------------------------ views

    /** Everything is public, so every player and every spectator gets the same view. */
    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        if (simulation == null) {
            return null;
        }
        boolean shrinking = shrinkFrom >= 0;
        return new SnakeArenaView(
                arena.cols(),
                arena.rows(),
                arena.name(),
                variation.key(),
                powerUps,
                (int) TICK_MS,
                Simulation.START_DELAY_TICKS,
                variation.respawns() ? Simulation.RESPAWN_TICKS : 0,
                shrinking
                        ? new ShrinkView(
                                (int) shrinkFrom,
                                Simulation.SHRINK_EVERY_TICKS,
                                Simulation.MIN_WIDTH,
                                Simulation.MIN_HEIGHT)
                        : null,
                roster,
                simulation.frame(ctx.round()),
                revealed ? results(ctx) : null);
    }

    /** The round's table for the reveal: players still in the game, best first. */
    private List<ResultRow> results(GameContext ctx) {
        List<ResultRow> rows = new ArrayList<>();
        for (Snake snake : simulation.snakes()) {
            if (ctx.player(snake.id()).isEmpty()) {
                continue;
            }
            rows.add(
                    new ResultRow(
                            snake.id(),
                            snake.points(),
                            snake.food(),
                            snake.credits(),
                            snake.crashes(),
                            snake.maxLength(),
                            summary != null && summary.longest().contains(snake.id()),
                            summary != null && summary.survivors().contains(snake.id())));
        }
        rows.sort(
                Comparator.comparingInt(ResultRow::points)
                        .thenComparingInt(ResultRow::maxLength)
                        .reversed());
        return rows;
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return stats.awards(ctx.players(), ctx.scores());
    }

    /**
     * For end-to-end tests: the frame plus every snake's name, head position and heading, so a test
     * can steer deterministically.
     */
    @Override
    public Object debugState(GameContext ctx) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (simulation == null) {
            return state;
        }
        Board board = simulation.board();
        state.put("round", ctx.round());
        state.put("tick", tick);
        state.put("cols", board.cols());
        state.put("rows", board.rows());
        state.put("variation", variation.key());
        state.put("revealed", revealed);
        List<Map<String, Object>> snakes = new ArrayList<>();
        for (Snake snake : simulation.snakes()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", snake.id());
            entry.put("name", nameOf(ctx, snake.id()));
            entry.put("alive", snake.alive());
            entry.put("dir", snake.dir().name());
            if (snake.alive()) {
                entry.put("x", board.x(snake.head()));
                entry.put("y", board.y(snake.head()));
            }
            entry.put("length", snake.length());
            entry.put("points", snake.points());
            snakes.add(entry);
        }
        state.put("snakes", snakes);
        state.put("frame", simulation.frame(ctx.round()));
        return state;
    }

    /** The current simulation (tests). */
    Simulation simulation() {
        return simulation;
    }

    private String nameOf(GameContext ctx, String playerId) {
        return ctx.player(playerId)
                .map(PlayerInfo::name)
                .orElseGet(
                        () ->
                                roster.stream()
                                        .filter(entry -> entry.id().equals(playerId))
                                        .map(RosterEntry::name)
                                        .findFirst()
                                        .orElse("Someone"));
    }
}
