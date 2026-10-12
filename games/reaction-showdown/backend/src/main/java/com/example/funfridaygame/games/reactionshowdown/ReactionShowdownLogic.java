package com.example.funfridaygame.games.reactionshowdown;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.Entry;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.MyStatus;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.PlayerStatus;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.SignalResult;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.TableRow;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules of Reaction Showdown.
 *
 * <p>A round is one turn with several signals. Each signal runs WAITING → (FAKE) → GO → RESULT,
 * driven by a turn-scoped timer whose key carries the signal id, so a timer can never fire into
 * another signal or round (BUG-25). A signal ends as soon as every connected player has tapped or
 * jumped the gun (BUG-06), otherwise when the GO window closes. After the last signal's mini-result
 * the turn ends and the platform shows the reveal with the round table (BUG-33).
 *
 * <p>Reaction times are measured by each browser from the moment it rendered GO, so network latency
 * doesn't decide who is fastest (IMP-21). The server checks every claim: taps before GO (or before
 * GO reached the player's screen) and anything under {@value #ANTICIPATION_MS} ms are false starts,
 * and a claim longer than the server's own elapsed time plus {@value #CLOCK_TOLERANCE_MS} ms is
 * replaced by the server's time.
 */
final class ReactionShowdownLogic implements GameLogic {

    static final String TAP = "tap";

    /** How long GO stays open when not everyone taps. */
    static final long GO_WINDOW_MS = 2000;

    /** How long a signal's mini-result is shown before the next signal. */
    static final long RESULT_MS = 1500;

    /** Extra time on top of the longest possible round before the platform's deadline hits. */
    static final long DEADLINE_SLACK_MS = 2000;

    /** Faster reactions are physically implausible: they count as anticipation (false start). */
    static final int ANTICIPATION_MS = 100;

    /** Allowed difference between a client's claim and the server's elapsed time. */
    static final int CLOCK_TOLERANCE_MS = 150;

    private static final String TIMER_PREFIX = "signal-";

    private final int signalsPerRound;
    private final boolean fakeouts;
    private final GameStats stats = new GameStats();
    private final List<SignalPlan> plans = new ArrayList<>();
    private final List<SignalResult> results = new ArrayList<>();
    private Signal current;
    private int lastSignalId;
    private long nextStepAt;
    private boolean finished;
    private boolean revealed;
    private List<TableRow> table = List.of();

    /**
     * Payload of the {@value #TAP} action.
     *
     * @param signal id of the signal the player reacted to
     * @param ms reaction time measured by the browser since it rendered GO; absent when the player
     *     tapped before seeing GO
     */
    record Tap(@NotNull @Positive Integer signal, @PositiveOrZero @Max(60_000) Integer ms) {}

    /**
     * Creates the logic for one game.
     *
     * @param signalsPerRound signals in every round
     * @param fakeouts whether decoys may flash before GO
     */
    ReactionShowdownLogic(int signalsPerRound, boolean fakeouts) {
        this.signalsPerRound = signalsPerRound;
        this.fakeouts = fakeouts;
    }

    /**
     * The turn deadline: the longest a round can possibly take plus some slack. It is a constant on
     * purpose — a deadline computed from the drawn plans would let a client work out when the last
     * GO comes (BUG-31).
     */
    static long maxTurnMillis(int signals, boolean fakeouts) {
        return signals * (SignalPlan.maxLeadIn(fakeouts) + GO_WINDOW_MS + RESULT_MS)
                + DEADLINE_SLACK_MS;
    }

    // ------------------------------------------------------------------ turn

    @Override
    public void startTurn(GameContext ctx) {
        plans.clear();
        results.clear();
        table = List.of();
        finished = false;
        revealed = false;
        for (int i = 0; i < signalsPerRound; i++) {
            plans.add(SignalPlan.draw(ctx.random(), fakeouts));
        }
        ctx.setDeadline(Duration.ofMillis(maxTurnMillis(signalsPerRound, fakeouts)));
        startSignal(ctx, 0);
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!TAP.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        Tap tap = action.payload(Tap.class);
        Signal signal = current;
        if (signal == null || revealed || tap.signal() != signal.id()) {
            throw new InvalidActionException("That signal is over — get ready for the next one");
        }
        if (!signal.state().acceptsTaps()) {
            throw new InvalidActionException("Too late — that signal is over");
        }
        Reaction previous = signal.reaction(player.id());
        if (previous != null) {
            throw new InvalidActionException(
                    previous.isValid()
                            ? "You already tapped this signal"
                            : "False start — wait for the next signal");
        }
        signal.record(player.id(), judge(ctx, signal, tap));
        if (everyoneReacted(ctx)) {
            finishSignal(ctx);
        }
    }

    @Override
    public void onTimer(GameContext ctx, String key) {
        Signal signal = current;
        if (signal == null || !key.equals(timerKey(signal))) {
            return;
        }
        switch (signal.state()) {
            case WAITING -> {
                if (signal.plan().hasFake()) {
                    signal.showFake();
                    schedule(ctx, signal.plan().fakeMillis());
                } else {
                    go(ctx);
                }
            }
            case FAKE -> go(ctx);
            case GO -> finishSignal(ctx);
            case RESULT -> {
                if (signal.index() + 1 < plans.size()) {
                    startSignal(ctx, signal.index() + 1);
                } else {
                    finished = true;
                }
            }
            default -> throw new IllegalStateException("Unknown state " + signal.state());
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        return finished;
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        if (current != null && current.state() != SignalState.RESULT) {
            closeSignal(ctx);
        }
        revealed = true;
        table = RoundTable.of(results, ctx.players());
        TurnOutcome.Builder outcome = TurnOutcome.builder().headline(headline(ctx));
        RoundTable.points(results).forEach(outcome::add);
        return outcome.build();
    }

    @Override
    public void onPlayerLeft(GameContext ctx, PlayerInfo player) {
        if (!revealed && everyoneReacted(ctx)) {
            finishSignal(ctx);
        }
    }

    // ------------------------------------------------------------------ views

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        Signal signal = current;
        if (signal == null) {
            return null;
        }
        List<PlayerStatus> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            Reaction reaction = signal.reaction(player.id());
            players.add(
                    new PlayerStatus(
                            player.id(),
                            reaction != null && reaction.isValid(),
                            reaction != null && !reaction.isValid()));
        }
        FakeOut fake = signal.visibleFake();
        return new ReactionShowdownView(
                signal.index() + 1,
                signalsPerRound,
                signal.id(),
                signal.state().name(),
                fake == null ? null : fake.name(),
                fakeouts,
                revealed,
                viewer.isSpectator() ? null : myStatus(viewer.playerId(), signal),
                players,
                List.copyOf(results),
                revealed ? table : null);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return stats.awards(ctx.players());
    }

    /** Test-only state: the current signal and whether a fake-out is still to come. */
    @Override
    public Object debugState(GameContext ctx) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (current != null) {
            state.put("signalId", current.id());
            state.put("signal", current.index() + 1);
            state.put("state", current.state().name());
            state.put("fakeComing", current.fakeComing());
            state.put("nextInMs", Math.max(0, nextStepAt - ctx.now()));
        }
        state.put("signals", signalsPerRound);
        state.put("fakeouts", fakeouts);
        return state;
    }

    // ------------------------------------------------------------------ signals

    private void startSignal(GameContext ctx, int index) {
        lastSignalId++;
        current = new Signal(lastSignalId, index, plans.get(index));
        schedule(ctx, current.plan().waitMillis());
    }

    private void go(GameContext ctx) {
        current.go(ctx.now());
        schedule(ctx, GO_WINDOW_MS);
    }

    private void finishSignal(GameContext ctx) {
        closeSignal(ctx);
        schedule(ctx, RESULT_MS);
    }

    /** Scores the current signal (also used when the deadline cuts a signal short). */
    private void closeSignal(GameContext ctx) {
        SignalResult result = SignalScoring.score(current, ctx.players());
        current.finish(result);
        results.add(result);
        stats.record(result);
    }

    /** (Re)schedules the current signal's single timer; its key binds it to this signal. */
    private void schedule(GameContext ctx, long delayMillis) {
        nextStepAt = ctx.now() + delayMillis;
        ctx.schedule(timerKey(current), Duration.ofMillis(delayMillis));
    }

    private static String timerKey(Signal signal) {
        return TIMER_PREFIX + signal.id();
    }

    /** Whether every connected player has tapped or jumped the gun on the open signal. */
    private boolean everyoneReacted(GameContext ctx) {
        if (current == null || !current.state().acceptsTaps()) {
            return false;
        }
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(p -> current.hasReacted(p.id()));
    }

    private Reaction judge(GameContext ctx, Signal signal, Tap tap) {
        return switch (signal.state()) {
            case WAITING -> Reaction.falseStart(Reaction.Foul.EARLY);
            case FAKE -> Reaction.falseStart(Reaction.Foul.FAKE);
            case GO -> timed(ctx.now() - signal.goAt(), tap.ms());
            case RESULT -> throw new IllegalStateException("The signal is over");
        };
    }

    /**
     * Judges a tap during GO.
     *
     * @param serverElapsed milliseconds since GO on the server (includes both network trips)
     * @param clientMs the browser's measurement since it rendered GO, or {@code null} if it hadn't
     */
    static Reaction timed(long serverElapsed, Integer clientMs) {
        if (clientMs == null) {
            return Reaction.falseStart(Reaction.Foul.EARLY);
        }
        long ms = clientMs > serverElapsed + CLOCK_TOLERANCE_MS ? serverElapsed : clientMs;
        if (ms < ANTICIPATION_MS) {
            return Reaction.falseStart(Reaction.Foul.EARLY);
        }
        return Reaction.valid((int) ms);
    }

    private MyStatus myStatus(String playerId, Signal signal) {
        Reaction reaction = signal.reaction(playerId);
        int roundPoints = RoundTable.points(results).getOrDefault(playerId, 0);
        if (reaction == null) {
            return new MyStatus(false, null, false, false, roundPoints);
        }
        return new MyStatus(
                reaction.isValid(),
                reaction.ms(),
                !reaction.isValid(),
                reaction.onFake(),
                roundPoints);
    }

    private String headline(GameContext ctx) {
        Entry fastest = null;
        boolean anyFalseStart = false;
        for (SignalResult result : results) {
            for (Entry entry : result.entries()) {
                anyFalseStart |= entry.falseStart();
                if (entry.ms() != null && (fastest == null || entry.ms() < fastest.ms())) {
                    fastest = entry;
                }
            }
        }
        if (fastest == null) {
            return anyFalseStart
                    ? "🐇 Jumpy fingers — nobody beat the signal this round"
                    : "😴 Nobody tapped this round";
        }
        String name = ctx.player(fastest.playerId()).map(PlayerInfo::name).orElse("Someone");
        return "⚡ " + name + " was quickest — " + fastest.ms() + " ms";
    }
}
