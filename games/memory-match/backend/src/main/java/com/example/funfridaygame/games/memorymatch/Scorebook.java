package com.example.funfridaygame.games.memorymatch;

import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pairs, points and streaks: per round for scoring and the live view, per game for the awards.
 *
 * <p>Scoring: every pair is worth {@value #PAIR_POINTS}; each further pair in the same streak
 * (consecutive matches without a miss) adds {@value #STREAK_BONUS}; at the end of a round the
 * player(s) with the most pairs earn {@value #MOST_PAIRS_BONUS}.
 */
final class Scorebook {

    static final int PAIR_POINTS = 100;
    static final int STREAK_BONUS = 50;
    static final int MOST_PAIRS_BONUS = 100;

    private final Map<String, Integer> roundPairs = new HashMap<>();
    private final Map<String, Integer> roundPoints = new HashMap<>();
    private final Tally totalPairs = new Tally();
    private final Tally luckyPairs = new Tally();
    private final Tally bestStreaks = new Tally();
    private long events;

    /** A number per player plus when it last grew, so ties go to whoever got there first. */
    private static final class Tally {
        private final Map<String, Integer> values = new HashMap<>();
        private final Map<String, Long> reachedAt = new HashMap<>();

        void increment(String playerId, long event) {
            values.merge(playerId, 1, Integer::sum);
            reachedAt.put(playerId, event);
        }

        void raiseTo(String playerId, int value, long event) {
            if (value > values.getOrDefault(playerId, 0)) {
                values.put(playerId, value);
                reachedAt.put(playerId, event);
            }
        }

        /** The highest value among players still in the game; ties go to the earliest. */
        Optional<Map.Entry<String, Integer>> leader(GameContext ctx) {
            Map.Entry<String, Integer> best = null;
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (ctx.player(entry.getKey()).isEmpty()) {
                    continue;
                }
                if (best == null
                        || entry.getValue() > best.getValue()
                        || (entry.getValue().equals(best.getValue())
                                && reachedAt.get(entry.getKey()) < reachedAt.get(best.getKey()))) {
                    best = entry;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /** Forgets the previous round's pairs and points (game totals stay). */
    void startRound() {
        roundPairs.clear();
        roundPoints.clear();
    }

    /**
     * Records a pair.
     *
     * @param playerId who found it
     * @param streak pairs in a row in the current streak, including this one
     * @param lucky whether the second card had never been face up before
     * @return the points the pair earns
     */
    int pair(String playerId, int streak, boolean lucky) {
        events++;
        int points = PAIR_POINTS + (streak > 1 ? STREAK_BONUS : 0);
        roundPairs.merge(playerId, 1, Integer::sum);
        roundPoints.merge(playerId, points, Integer::sum);
        totalPairs.increment(playerId, events);
        bestStreaks.raiseTo(playerId, streak, events);
        if (lucky) {
            luckyPairs.increment(playerId, events);
        }
        return points;
    }

    /** Pairs the player found this round. */
    int roundPairs(String playerId) {
        return roundPairs.getOrDefault(playerId, 0);
    }

    /** Points the player earned from pairs this round (without the end-of-round bonus). */
    int roundPoints(String playerId) {
        return roundPoints.getOrDefault(playerId, 0);
    }

    /**
     * Players still in the game with the most pairs this round (at least one pair); each of them
     * earns the round bonus.
     */
    Set<String> mostPairs(GameContext ctx) {
        int most = 0;
        for (PlayerInfo player : ctx.players()) {
            most = Math.max(most, roundPairs(player.id()));
        }
        Set<String> leaders = new LinkedHashSet<>();
        for (PlayerInfo player : ctx.players()) {
            if (most > 0 && roundPairs(player.id()) == most) {
                leaders.add(player.id());
            }
        }
        return leaders;
    }

    /**
     * Fun titles: the longest streak (at least two pairs in a row), the most pairs overall and the
     * most lucky pairs (the second card had never been seen before). Players who left are skipped.
     */
    List<Award> awards(GameContext ctx) {
        List<Award> awards = new ArrayList<>();
        bestStreaks
                .leader(ctx)
                .filter(e -> e.getValue() >= 2)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🐘",
                                                "Elephant memory",
                                                e.getKey(),
                                                e.getValue() + " pairs in a row")));
        totalPairs
                .leader(ctx)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🃏",
                                                "Pair collector",
                                                e.getKey(),
                                                plural(e.getValue(), "pair"))));
        luckyPairs
                .leader(ctx)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🍀",
                                                "Lucky first try",
                                                e.getKey(),
                                                plural(e.getValue(), "lucky pair"))));
        return awards;
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
