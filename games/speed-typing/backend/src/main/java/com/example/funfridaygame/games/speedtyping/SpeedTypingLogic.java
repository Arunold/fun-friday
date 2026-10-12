package com.example.funfridaygame.games.speedtyping;

import com.example.funfridaygame.games.speedtyping.SpeedTypingView.Lane;
import com.example.funfridaygame.games.speedtyping.SpeedTypingView.MyRace;
import com.example.funfridaygame.games.speedtyping.SpeedTypingView.ResultRow;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rules of Speed Typing. Every round all players type the same text. Browsers report what their
 * player typed; the server decides the progress (correct prefix), the finish (exact match) and the
 * finishing order, and scores the round with {@link Scoring}. A round ends when every connected
 * player finished or when the timer (per text length, or the host's choice) runs out.
 */
final class SpeedTypingLogic implements GameLogic {

    static final String PROGRESS = "progress";

    /** Characters a player may type past the end of the text (they show up as errors to fix). */
    static final int OVERFLOW = 10;

    /** Longest typed text accepted at all: the longest possible text (260) plus the overflow. */
    static final int MAX_TYPED = 270;

    private static final int MAX_COUNT = 100_000;

    /** Finishers by place, then everyone else by progress (join order breaks ties). */
    private static final Comparator<ResultRow> RESULT_ORDER =
            Comparator.comparing((ResultRow row) -> !row.finished())
                    .thenComparingInt(row -> row.rank() == null ? Integer.MAX_VALUE : row.rank())
                    .thenComparing(Comparator.comparingInt(ResultRow::progress).reversed());

    private final RaceConfig config;
    private final TextLibrary library;
    private final Set<String> usedTexts = new HashSet<>();
    private final Map<String, Racer> racers = new HashMap<>();
    private final GameStats stats = new GameStats();
    private String text = "";
    private int finishers;
    private boolean revealed;
    private long turnEndedAt;
    private List<ResultRow> results = List.of();

    /**
     * Payload of the {@value #PROGRESS} action, sent about every 150 ms while typing and once more
     * when the text matches.
     *
     * @param typed everything typed so far
     * @param keystrokes characters typed in total, including corrected ones
     * @param errors characters that were wrong when typed
     */
    record Progress(
            @NotNull @Size(max = MAX_TYPED) String typed,
            @NotNull @Min(0) @Max(MAX_COUNT) Integer keystrokes,
            @NotNull @Min(0) @Max(MAX_COUNT) Integer errors) {}

    SpeedTypingLogic(RaceConfig config, TextLibrary library) {
        this.config = config;
        this.library = library;
    }

    @Override
    public void startTurn(GameContext ctx) {
        text = library.pick(config.length(), usedTexts, ctx.random());
        usedTexts.add(text);
        racers.clear();
        finishers = 0;
        revealed = false;
        results = List.of();
        ctx.setDeadline(config.roundDuration());
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!PROGRESS.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        Progress progress = action.payload(Progress.class);
        if (progress.typed().length() > text.length() + OVERFLOW) {
            throw new InvalidActionException(
                    "That's longer than the text, please delete the extra characters");
        }
        if (progress.errors() > progress.keystrokes()) {
            throw new InvalidActionException("There can't be more errors than keystrokes");
        }
        Racer racer = racers.computeIfAbsent(player.id(), id -> new Racer(ctx.turnStartedAt()));
        Racer.Update update =
                racer.apply(
                        text,
                        progress.typed(),
                        progress.keystrokes(),
                        progress.errors(),
                        ctx.now());
        if (update == Racer.Update.FINISHED) {
            racer.setRank(++finishers);
        } else if (update != Racer.Update.CHANGED) {
            // Same text, a race that is already over, or faster than humanly possible: the views
            // stay as they are (the browser keeps syncing, so nothing is lost).
            ctx.suppressUpdate();
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(player -> finished(player.id()));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        turnEndedAt = ctx.now();
        TurnOutcome.Builder outcome = TurnOutcome.builder();
        List<ResultRow> rows = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            Racer racer = racers.get(player.id());
            if (racer == null) {
                rows.add(new ResultRow(player.id(), false, null, 0, null, null, null, false));
                continue;
            }
            double wpm = racer.wpm(turnEndedAt);
            boolean penalized = penalized(racer);
            if (racer.typedAnything()) {
                outcome.add(player.id(), points(racer, wpm, penalized));
            }
            stats.record(player.id(), racer, wpm);
            rows.add(row(player.id(), racer, wpm, penalized));
        }
        rows.sort(RESULT_ORDER);
        results = List.copyOf(rows);
        return outcome.headline(headline(ctx)).build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        List<Lane> lanes = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            lanes.add(lane(player.id(), ctx.now()));
        }
        MyRace me = viewer.isSpectator() ? null : myRace(racers.get(viewer.playerId()), ctx.now());
        return new SpeedTypingView(
                text,
                config.length().name(),
                config.minAccuracy(),
                ctx.deadline(),
                revealed,
                me,
                lanes,
                revealed ? results : null);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return stats.awards(ctx.players());
    }

    @Override
    public Object debugState(GameContext ctx) {
        return Map.of("text", text, "difficulty", config.length().name());
    }

    // ------------------------------------------------------------------ helpers

    private boolean finished(String playerId) {
        Racer racer = racers.get(playerId);
        return racer != null && racer.finished();
    }

    private boolean penalized(Racer racer) {
        return racer.finished() && racer.belowAccuracy(config.minAccuracy());
    }

    private int points(Racer racer, double wpm, boolean penalized) {
        return racer.finished()
                ? Scoring.finisher(racer.rank(), wpm, racer.accuracy(), penalized)
                : Scoring.unfinished(racer.correct(), text.length());
    }

    private ResultRow row(String playerId, Racer racer, double wpm, boolean penalized) {
        boolean typed = racer.typedAnything();
        return new ResultRow(
                playerId,
                racer.finished(),
                racer.finished() ? racer.rank() : null,
                racer.progress(text.length()),
                typed ? (int) Math.round(wpm) : null,
                typed ? racer.accuracyPercent() : null,
                racer.timeMillis(),
                penalized);
    }

    /** A lane: speed and place are public once the player finished. */
    private Lane lane(String playerId, long now) {
        Racer racer = racers.get(playerId);
        if (racer == null) {
            return new Lane(playerId, 0, false, null, null);
        }
        boolean done = racer.finished();
        return new Lane(
                playerId,
                racer.progress(text.length()),
                done,
                done ? racer.rank() : null,
                done ? (int) Math.round(racer.wpm(now)) : null);
    }

    /** The viewer's own race, including what they typed (only ever sent to them). */
    private MyRace myRace(Racer racer, long now) {
        if (racer == null) {
            return new MyRace("", 0, 0, null, 0, false, null, null, null, null, false);
        }
        boolean done = racer.finished();
        boolean showStats = (done || revealed) && racer.typedAnything();
        double wpm = racer.wpm(revealed ? turnEndedAt : now);
        return new MyRace(
                racer.typed(),
                racer.keystrokes(),
                racer.errors(),
                racer.startedAt(),
                racer.progress(text.length()),
                done,
                done ? racer.rank() : null,
                showStats ? (int) Math.round(wpm) : null,
                showStats ? racer.accuracyPercent() : null,
                racer.timeMillis(),
                penalized(racer));
    }

    /** The reveal banner: the winner and their speed, or who got furthest. */
    private String headline(GameContext ctx) {
        if (results.isEmpty()) {
            return "Time's up!";
        }
        ResultRow best = results.getFirst();
        String name = ctx.player(best.playerId()).map(PlayerInfo::name).orElse("Someone");
        if (best.finished()) {
            boolean flawless = Integer.valueOf(100).equals(best.accuracy());
            return name
                    + " wins the race at "
                    + best.wpm()
                    + " WPM"
                    + (flawless ? ", without a single typo!" : "!");
        }
        if (best.progress() > 0) {
            return "Time's up! " + name + " got furthest (" + best.progress() + "%)";
        }
        return "Time's up! Nobody made it off the starting line";
    }
}
