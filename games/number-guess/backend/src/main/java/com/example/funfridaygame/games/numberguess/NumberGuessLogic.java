package com.example.funfridaygame.games.numberguess;

import com.example.funfridaygame.games.numberguess.NumberGuessView.GuessView;
import com.example.funfridaygame.games.numberguess.NumberGuessView.PlayerStatus;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules of Number Guess.
 *
 * <p>Scoring per round: an exact hit on guess {@code k} earns {@code 100 - 20·(k-1)} (at least 40)
 * plus up to 20 points for speed. Without a hit, the best guess earns up to 50 points depending on
 * how close it was.
 */
final class NumberGuessLogic implements GameLogic {

    static final String GUESS = "guess";

    private final int min;
    private final int max;
    private final int attempts;
    private final Map<String, Attempts> byPlayer = new LinkedHashMap<>();
    private final Map<String, Integer> exactHits = new HashMap<>();
    private final Map<String, Long> fastestHitMillis = new HashMap<>();
    private int target;
    private boolean revealed;

    /** Payload of the {@value #GUESS} action. */
    record Guess(@NotNull Integer value) {}

    private enum Hint {
        HIGHER,
        LOWER,
        CORRECT
    }

    private static final class Attempts {
        private final List<GuessView> guesses = new ArrayList<>();
        private long hitAt = -1;

        boolean exact() {
            return hitAt >= 0;
        }
    }

    NumberGuessLogic(int min, int max, int attempts) {
        this.min = min;
        this.max = max;
        this.attempts = attempts;
    }

    @Override
    public void startTurn(GameContext ctx) {
        target = min + ctx.random().nextInt(max - min + 1);
        byPlayer.clear();
        revealed = false;
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!GUESS.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        int value = action.payload(Guess.class).value();
        if (value < min || value > max) {
            throw new InvalidActionException("Guess a number from " + min + " to " + max);
        }
        Attempts mine = byPlayer.computeIfAbsent(player.id(), id -> new Attempts());
        if (isDone(mine)) {
            throw new InvalidActionException("You have no guesses left this round");
        }
        Hint hint = value == target ? Hint.CORRECT : value < target ? Hint.HIGHER : Hint.LOWER;
        mine.guesses.add(new GuessView(value, hint.name()));
        if (hint == Hint.CORRECT) {
            mine.hitAt = ctx.now();
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(p -> isDone(byPlayer.get(p.id())));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        TurnOutcome.Builder outcome = TurnOutcome.builder().headline("The number was " + target);
        long turnLength = Math.max(1, ctx.deadline() - ctx.turnStartedAt());
        byPlayer.forEach(
                (playerId, mine) -> {
                    if (mine.guesses.isEmpty()) {
                        return;
                    }
                    if (mine.exact()) {
                        int base = Math.max(40, 100 - 20 * (mine.guesses.size() - 1));
                        double remaining = Math.max(0, ctx.deadline() - mine.hitAt);
                        int speed = (int) Math.round(20 * remaining / turnLength);
                        outcome.add(playerId, base + speed);
                        exactHits.merge(playerId, 1, Integer::sum);
                        fastestHitMillis.merge(
                                playerId, mine.hitAt - ctx.turnStartedAt(), Math::min);
                    } else {
                        int distance = Math.abs(best(mine) - target);
                        double closeness = 1.0 - (double) distance / (max - min);
                        outcome.add(playerId, (int) Math.round(50 * Math.max(0, closeness)));
                    }
                });
        return outcome.build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        List<PlayerStatus> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            Attempts state = byPlayer.get(player.id());
            int used = state == null ? 0 : state.guesses.size();
            boolean done = isDone(state);
            players.add(
                    revealed
                            ? new PlayerStatus(
                                    player.id(),
                                    used,
                                    done,
                                    state != null && state.exact(),
                                    state == null ? List.of() : List.copyOf(state.guesses),
                                    state == null || state.guesses.isEmpty() ? null : best(state))
                            : new PlayerStatus(player.id(), used, done, null, null, null));
        }
        List<GuessView> mine = null;
        String status = null;
        if (!viewer.isSpectator()) {
            Attempts state = byPlayer.get(viewer.playerId());
            mine = state == null ? List.of() : List.copyOf(state.guesses);
            status =
                    state != null && state.exact()
                            ? "EXACT"
                            : isDone(state) ? "OUT_OF_GUESSES" : "GUESSING";
        }
        return new NumberGuessView(
                min, max, attempts, revealed, revealed ? target : null, mine, status, players);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        List<Award> awards = new ArrayList<>();
        exactHits.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🎯",
                                                "Bullseye",
                                                e.getKey(),
                                                e.getValue()
                                                        + (e.getValue() == 1
                                                                ? " exact hit"
                                                                : " exact hits"))));
        fastestHitMillis.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "⚡",
                                                "Quick draw",
                                                e.getKey(),
                                                String.format("%.1f s", e.getValue() / 1000.0))));
        return awards;
    }

    @Override
    public Object debugState(GameContext ctx) {
        return Map.of("target", target);
    }

    private boolean isDone(Attempts state) {
        return state != null && (state.exact() || state.guesses.size() >= attempts);
    }

    private int best(Attempts state) {
        int best = state.guesses.getFirst().value();
        for (GuessView guess : state.guesses) {
            if (Math.abs(guess.value() - target) < Math.abs(best - target)) {
                best = guess.value();
            }
        }
        return best;
    }
}
