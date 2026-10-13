package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.AreaView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.FrameView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.LevelResult;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.LevelView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.SeatView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.SignalView;
import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Handling;
import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Pedals;
import com.example.funfridaygame.games.backseatdrivers.InputBook.TapBatch;
import com.example.funfridaygame.games.backseatdrivers.Seating.Seat;
import com.example.funfridaygame.games.backseatdrivers.Simulation.Status;
import com.example.funfridaygame.games.backseatdrivers.Simulation.Step;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.Viewer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One level in progress: runs the tick loop (inputs → simulation → frame), the SEAT SHUFFLE clock
 * and the roster (players dropping out), moves the deadline for cones and coffee, and builds the
 * level part of every view.
 *
 * <p>Each tick streams a compact {@link FrameView} to everyone and, when the bus entered another
 * cell or a hazard changed, the drivers' {@link AreaView window}. A tick asks for a snapshot (by
 * returning {@code true}) only when something the views show changed: roles, the shuffle siren, the
 * deadline, the hazards.
 */
final class LevelRun {

    /** Timer key of the tick loop. */
    static final String TICK_TIMER = "tick";

    /** Length of a tick (20 Hz). */
    static final long TICK_MILLIS = 50;

    /** The siren sounds this long before each shuffle. */
    static final long SHUFFLE_WARNING_MILLIS = 2000;

    /** A silent navigator can signal at most this often. */
    static final long SIGNAL_GAP_MILLIS = 500;

    /** Seconds a cone costs and a coffee gives. */
    static final int TIME_PER_HAZARD_SECONDS = 5;

    /** Stream event with a {@link FrameView}. */
    static final String FRAME_EVENT = "frame";

    /** Stream event with the drivers' {@link AreaView window}. */
    static final String WINDOW_EVENT = "window";

    /** Stream event with a silent-navigator {@link SignalView}. */
    static final String SIGNAL_EVENT = "signal";

    private final int number;
    private final Rules rules;
    private final Simulation sim;
    private final Seating seating;
    private final GameStats stats;
    private final InputBook inputs = new InputBook();
    private long tick;
    private long startedAt;
    private long nextShuffleAt;
    private boolean warning;
    private int windowVersion = 1;
    private List<String> connected = List.of();
    private FrameView frame;
    private SignalView signal;
    private int signals;
    private long lastSignalAt = Long.MIN_VALUE / 2;
    private LevelResult result;

    /**
     * A level about to start.
     *
     * @param number level number (1 to 3)
     * @param map the level's map
     * @param rules the game's settings
     * @param seating the game's seating (it lives across levels)
     * @param stats the game's award statistics
     */
    LevelRun(int number, TrackMap map, Rules rules, Seating seating, GameStats stats) {
        this.number = number;
        this.rules = rules;
        this.sim = new Simulation(map);
        this.seating = seating;
        this.stats = stats;
    }

    /** Deals the seats and starts the tick loop. */
    void start(GameContext ctx) {
        startedAt = ctx.turnStartedAt();
        connected = activeIds(ctx);
        seating.deal(connected, Seating.Mode.SHUFFLE, ctx.random());
        nextShuffleAt = startedAt + rules.shuffleMillis();
        frame = frame(ctx.now());
        ctx.schedule(TICK_TIMER, Duration.ofMillis(TICK_MILLIS));
    }

    /**
     * One tick: roster, shuffle clock, inputs, simulation, deadline changes, streams.
     *
     * @return whether the views changed (a snapshot is needed)
     */
    boolean tick(GameContext ctx) {
        if (result != null || sim.status() != Status.DRIVING) {
            return false;
        }
        tick++;
        long now = ctx.now();
        boolean changed = syncRoster(ctx);
        changed |= runShuffleClock(ctx, now);
        Pedals pedals = inputs.pedals(seating);
        TapBatch taps = inputs.takeTaps();
        Step step = sim.step(pedals, taps.taps(), handling(now), now);
        record(step, pedals, taps);
        int seconds = (step.coffees() - step.conesHit()) * TIME_PER_HAZARD_SECONDS;
        if (step.coffees() > 0 || step.conesHit() > 0) {
            ctx.extendDeadline(Duration.ofSeconds(seconds));
            changed = true;
        }
        if (step.cellChanged() || step.hazardsChanged()) {
            windowVersion++;
            ctx.stream(WINDOW_EVENT, window());
        }
        changed |= step.hazardsChanged();
        frame = frame(now);
        ctx.stream(FRAME_EVENT, frame);
        if (sim.status() != Status.DRIVING) {
            ctx.endTurn();
            return true;
        }
        long next = startedAt + (tick + 1) * TICK_MILLIS;
        ctx.schedule(TICK_TIMER, Duration.ofMillis(Math.max(1, next - now)));
        return changed;
    }

    private void record(Step step, Pedals pedals, TapBatch taps) {
        String navigator = seating.navigator();
        if (step.newCell() && navigator != null) {
            stats.newCell(navigator);
        }
        if (step.geeseScattered() > 0) {
            stats.scattered(taps.honkers(), step.geeseScattered());
        }
        if (pedals.gas() && !pedals.brake()) {
            seating.holders(Control.GAS).forEach(id -> stats.gas(id, TICK_MILLIS));
        }
    }

    private Handling handling(long now) {
        if (rules.variation() != Variation.RUSH_HOUR) {
            return Handling.classic();
        }
        return Handling.rushHour((now - startedAt) / (double) rules.levelMillis());
    }

    /** Players who dropped out release their controls; orphaned seats are dealt again at once. */
    private boolean syncRoster(GameContext ctx) {
        List<String> active = activeIds(ctx);
        if (active.equals(connected)) {
            return false;
        }
        List<String> lost = new ArrayList<>(connected);
        lost.removeAll(active);
        connected = active;
        lost.forEach(inputs::release);
        if (lost.stream().anyMatch(seating::seated)) {
            inputs.releaseAll();
            seating.deal(connected, Seating.Mode.REPAIR, ctx.random());
        }
        return true;
    }

    private boolean runShuffleClock(GameContext ctx, long now) {
        if (now >= nextShuffleAt) {
            warning = false;
            nextShuffleAt += rules.shuffleMillis();
            inputs.releaseAll();
            seating.deal(connected, Seating.Mode.SHUFFLE, ctx.random());
            return true;
        }
        if (!warning && now >= nextShuffleAt - SHUFFLE_WARNING_MILLIS) {
            warning = true;
            return true;
        }
        return false;
    }

    /** A player left the game for good. */
    void playerLeft(GameContext ctx, String playerId) {
        inputs.release(playerId);
        if (result == null) {
            syncRoster(ctx);
        }
    }

    /**
     * A key or button change. Ignored unless the player holds the control right now (a press that
     * arrives after a shuffle is simply dropped).
     */
    void input(String playerId, Control control, boolean pressed, long now) {
        if (result == null
                && sim.status() == Status.DRIVING
                && connected.contains(playerId)
                && seating.holds(playerId, control)) {
            inputs.press(playerId, control, pressed, now);
        }
    }

    /** A silent-navigator signal: only from the navigator, at most one per half second. */
    void signal(GameContext ctx, String playerId, SignalKind kind) {
        long now = ctx.now();
        if (result != null
                || !playerId.equals(seating.navigator())
                || now - lastSignalAt < SIGNAL_GAP_MILLIS) {
            return;
        }
        lastSignalAt = now;
        signal = new SignalView(++signals, kind.name(), now);
        ctx.stream(SIGNAL_EVENT, signal);
    }

    /** Ends the level and scores it (the timer ran out unless the bus parked or crashed). */
    LevelResult finish(GameContext ctx) {
        if (result != null) {
            return result;
        }
        String status =
                switch (sim.status()) {
                    case PARKED -> Scoring.PARKED;
                    case CRASHED -> Scoring.CRASHED;
                    case DRIVING -> Scoring.TIME_UP;
                };
        int secondsLeft =
                sim.status() == Status.PARKED ? Scoring.secondsLeft(ctx.remainingMillis()) : 0;
        inputs.releaseAll();
        warning = false;
        result =
                new LevelResult(
                        number,
                        sim.map().name(),
                        status,
                        secondsLeft,
                        sim.coffees(),
                        sim.conesHit(),
                        Scoring.levelScore(secondsLeft, sim.coffees(), sim.conesHit()));
        frame = frame(ctx.now());
        return result;
    }

    /** The level part of a viewer's view. */
    LevelView view(Viewer viewer) {
        boolean over = result != null;
        boolean navigating = !over && viewer.is(seating.navigator());
        List<String> mine =
                viewer.isSpectator()
                        ? null
                        : seating.controlsOf(viewer.playerId()).stream().map(Enum::name).toList();
        return new LevelView(
                number,
                sim.map().name(),
                over ? result.status() : "DRIVING",
                seating.version(),
                seats(),
                mine,
                navigating,
                over ? 0 : nextShuffleAt,
                !over && warning,
                frame,
                over || navigating ? null : window(),
                over || navigating ? Areas.fullMap(number, sim) : null,
                over ? sim.trail() : null,
                signal,
                result);
    }

    private List<SeatView> seats() {
        return seating.seats().stream()
                .map(
                        seat ->
                                new SeatView(
                                        seat.playerId(),
                                        seat.controls().stream().map(Enum::name).toList()))
                .toList();
    }

    private AreaView window() {
        return Areas.window(number, windowVersion, sim);
    }

    private FrameView frame(long now) {
        List<Seat> seats = seating.seats();
        int[] pressed = new int[seats.size()];
        for (int i = 0; i < seats.size(); i++) {
            pressed[i] = inputs.pressedMask(seats.get(i).playerId(), now);
        }
        Bus bus = sim.bus();
        int[] state = {
            (int) Math.round(bus.x() * 1000),
            (int) Math.round(bus.y() * 1000),
            (int) Math.round(bus.heading() * 1000),
            (int) Math.round(bus.speed() * 1000)
        };
        return new FrameView(
                number,
                tick,
                state,
                pressed,
                sim.effects(now),
                sim.conesHit(),
                sim.coffees(),
                sim.crossingMask(),
                seating.version());
    }

    private static List<String> activeIds(GameContext ctx) {
        return ctx.activePlayers().stream().map(PlayerInfo::id).toList();
    }

    int number() {
        return number;
    }

    Simulation simulation() {
        return sim;
    }

    long tickCount() {
        return tick;
    }

    long nextShuffleAt() {
        return nextShuffleAt;
    }

    boolean shuffleWarning() {
        return warning;
    }

    /** The result once the level is over, else {@code null}. */
    LevelResult result() {
        return result;
    }
}
