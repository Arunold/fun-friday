package com.example.funfridaygame.games.triviablitz;

import com.example.funfridaygame.games.triviablitz.TriviaBlitzView.Fastest;
import com.example.funfridaygame.games.triviablitz.TriviaBlitzView.PlayerStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rules of Trivia Blitz: one question per round, the same for everyone. Each player locks in one
 * answer; the turn ends when every connected player has answered or the timer runs out. Correct
 * answers score by speed plus a streak bonus (see {@link Scoring}).
 *
 * <p>Secrets: the correct option never leaves the server before the reveal, and other players'
 * picks stay hidden until then too — views only say <em>who</em> has answered (BUG-08).
 */
final class TriviaBlitzLogic implements GameLogic {

    static final String ANSWER = "answer";

    private static final String PICK = "must be A, B, C or D (0–3)";
    private static final List<String> LETTERS = List.of("A", "B", "C", "D");

    private final List<Question> pool;
    private final boolean rampUp;
    private final Map<String, PlayerRecord> records = new HashMap<>();
    private final Map<String, Pick> picks = new LinkedHashMap<>();
    private final Map<String, Integer> lostStreaks = new HashMap<>();
    private List<Question> plan = List.of();
    private Question current;
    private long turnMillis;
    private boolean revealed;
    private int asked;

    /** Payload of the {@value #ANSWER} action: the chosen option, 0 = A … 3 = D. */
    record Answer(
            @NotNull(message = PICK) @Min(value = 0, message = PICK) @Max(value = 3, message = PICK) Integer option) {}

    /** A locked-in answer and how long it took. */
    private record Pick(int option, long millis) {}

    /**
     * @param pool the questions allowed by the settings
     * @param rampUp whether the difficulty is mixed (balanced and ordered easy → hard)
     */
    TriviaBlitzLogic(List<Question> pool, boolean rampUp) {
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No questions match the settings");
        }
        this.pool = List.copyOf(pool);
        this.rampUp = rampUp;
    }

    @Override
    public void startTurn(GameContext ctx) {
        if (plan.isEmpty()) {
            plan = QuizPlanner.plan(pool, ctx.totalRounds(), rampUp, ctx.random());
        }
        int index = ctx.round() - 1;
        // The settings validation guarantees enough questions; the fallback only guards the engine.
        Question next =
                index < plan.size() ? plan.get(index) : pool.get(ctx.random().nextInt(pool.size()));
        current = next.shuffled(ctx.random());
        picks.clear();
        lostStreaks.clear();
        revealed = false;
        turnMillis = Math.max(1, ctx.deadline() - ctx.turnStartedAt());
        asked++;
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!ANSWER.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        int option = action.payload(Answer.class).option();
        if (picks.containsKey(player.id())) {
            throw new InvalidActionException("Your answer is already locked in");
        }
        picks.put(player.id(), new Pick(option, Math.max(0, ctx.now() - ctx.turnStartedAt())));
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(p -> picks.containsKey(p.id()));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        TurnOutcome.Builder outcome = TurnOutcome.builder();
        int right = 0;
        int answered = 0;
        for (PlayerInfo player : ctx.players()) {
            PlayerRecord record = records.computeIfAbsent(player.id(), id -> new PlayerRecord());
            Pick pick = picks.get(player.id());
            if (pick != null) {
                answered++;
            }
            if (isCorrect(pick)) {
                int streak = record.recordCorrect(pick.millis());
                outcome.add(player.id(), Scoring.points(pick.millis(), turnMillis, streak));
                right++;
            } else {
                int lost = record.recordMiss();
                if (lost >= 2) {
                    lostStreaks.put(player.id(), lost);
                }
            }
        }
        return outcome.headline(headline(right, answered, ctx.players().size())).build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        if (current == null) {
            return null;
        }
        List<PlayerInfo> players = ctx.players();
        List<PlayerStatus> statuses = new ArrayList<>(players.size());
        int answered = 0;
        for (PlayerInfo player : players) {
            Pick pick = picks.get(player.id());
            if (pick != null) {
                answered++;
            }
            statuses.add(status(player.id(), pick));
        }
        Integer myAnswer = null;
        Integer myStreak = null;
        if (!viewer.isSpectator()) {
            Pick mine = picks.get(viewer.playerId());
            myAnswer = mine == null ? null : mine.option();
            myStreak = streakOf(viewer.playerId());
        }
        return new TriviaBlitzView(
                ctx.round(),
                current.category().label(),
                current.category().icon(),
                current.difficulty().id(),
                current.text(),
                current.options(),
                revealed,
                myAnswer,
                myStreak,
                answered,
                statuses,
                revealed ? current.answer() : null,
                revealed ? pickers(players) : null,
                revealed ? fastest(players) : null,
                revealed ? current.fact() : null);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return TriviaAwards.of(ctx.players(), records, asked);
    }

    /** The correct option of the current question (e2e tests only). */
    @Override
    public Object debugState(GameContext ctx) {
        if (current == null) {
            return Map.of();
        }
        return Map.of(
                "correct", current.answer(),
                "answer", current.correctOption(),
                "question", current.text());
    }

    private PlayerStatus status(String playerId, Pick pick) {
        int streak = streakOf(playerId);
        if (!revealed) {
            return new PlayerStatus(playerId, pick != null, streak, null, null, null);
        }
        return new PlayerStatus(
                playerId,
                pick != null,
                streak,
                isCorrect(pick),
                pick == null ? null : pick.millis(),
                lostStreaks.get(playerId));
    }

    private int streakOf(String playerId) {
        PlayerRecord record = records.get(playerId);
        return record == null ? 0 : record.streak();
    }

    private boolean isCorrect(Pick pick) {
        return pick != null && pick.option() == current.answer();
    }

    /** Player ids per option, fastest first; players who left are not listed. */
    private List<List<String>> pickers(List<PlayerInfo> players) {
        Set<String> present = players.stream().map(PlayerInfo::id).collect(Collectors.toSet());
        List<List<String>> byOption = new ArrayList<>(Question.OPTIONS);
        for (int option = 0; option < Question.OPTIONS; option++) {
            int wanted = option;
            byOption.add(
                    picks.entrySet().stream()
                            .filter(e -> present.contains(e.getKey()))
                            .filter(e -> e.getValue().option() == wanted)
                            .sorted(Comparator.comparingLong(e -> e.getValue().millis()))
                            .map(Map.Entry::getKey)
                            .toList());
        }
        return byOption;
    }

    /** The fastest correct answer; on equal times the one received first. */
    private Fastest fastest(List<PlayerInfo> players) {
        Set<String> present = players.stream().map(PlayerInfo::id).collect(Collectors.toSet());
        return picks.entrySet().stream()
                .filter(e -> present.contains(e.getKey()) && isCorrect(e.getValue()))
                .min(Comparator.comparingLong(e -> e.getValue().millis()))
                .map(e -> new Fastest(e.getKey(), e.getValue().millis()))
                .orElse(null);
    }

    private String headline(int right, int answered, int players) {
        String answer = LETTERS.get(current.answer()) + ") " + current.correctOption();
        if (answered == 0) {
            return "Time's up — it was " + answer;
        }
        if (right == 0) {
            return "Nobody got it — it was " + answer;
        }
        if (right == players && players > 1) {
            return "Everyone got it — " + answer;
        }
        return "The answer was " + answer;
    }
}
