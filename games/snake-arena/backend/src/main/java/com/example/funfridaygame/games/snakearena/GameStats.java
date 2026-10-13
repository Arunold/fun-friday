package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Game-wide statistics behind the podium awards. */
final class GameStats {

    private final Map<String, Totals> byPlayer = new LinkedHashMap<>();

    /** One player's numbers over all rounds. */
    private static final class Totals {
        private int maxLength;
        private int credits;
        private int food;
        private int crashes;
    }

    /** Adds a finished round. */
    void record(Collection<Snake> snakes) {
        for (Snake snake : snakes) {
            Totals totals = byPlayer.computeIfAbsent(snake.id(), id -> new Totals());
            totals.maxLength = Math.max(totals.maxLength, snake.maxLength());
            totals.credits += snake.credits();
            totals.food += snake.food();
            totals.crashes += snake.crashes();
        }
    }

    /**
     * Biggest snake (longest length reached), Trapper (most snakes crashed into them), Glutton
     * (most food) and Survivor (fewest crashes). Ties go to the higher total score, then to the
     * earlier player in join order; Trapper and Glutton need at least one.
     */
    List<Award> awards(List<PlayerInfo> players, Map<String, Integer> scores) {
        List<Award> awards = new ArrayList<>();
        String biggest = best(players, scores, t -> t.maxLength, true);
        if (biggest != null) {
            int length = byPlayer.get(biggest).maxLength;
            awards.add(
                    new Award("🐍", "Biggest snake", biggest, "Grew to " + length + " segments"));
        }
        String trapper = best(players, scores, t -> t.credits, true);
        if (trapper != null && byPlayer.get(trapper).credits > 0) {
            int credits = byPlayer.get(trapper).credits;
            awards.add(
                    new Award(
                            "🕸️",
                            "Trapper",
                            trapper,
                            plural(credits, "snake", "snakes") + " ran into them"));
        }
        String glutton = best(players, scores, t -> t.food, true);
        if (glutton != null && byPlayer.get(glutton).food > 0) {
            int food = byPlayer.get(glutton).food;
            awards.add(
                    new Award("🍎", "Glutton", glutton, "Ate " + plural(food, "apple", "apples")));
        }
        String survivor = best(players, scores, t -> t.crashes, false);
        if (survivor != null) {
            int crashes = byPlayer.get(survivor).crashes;
            awards.add(
                    new Award(
                            "🛡️",
                            "Survivor",
                            survivor,
                            crashes == 0 ? "No crashes" : plural(crashes, "crash", "crashes")));
        }
        return awards;
    }

    /**
     * The player with the highest (or lowest) value; ties go to the higher score, then join order.
     */
    private String best(
            List<PlayerInfo> players,
            Map<String, Integer> scores,
            ToIntFunction<Totals> metric,
            boolean highest) {
        String best = null;
        int bestValue = 0;
        int bestScore = 0;
        for (PlayerInfo player : players) {
            Totals totals = byPlayer.get(player.id());
            if (totals == null) {
                continue;
            }
            int value = metric.applyAsInt(totals);
            int score = scores.getOrDefault(player.id(), 0);
            boolean better =
                    best == null
                            || (highest ? value > bestValue : value < bestValue)
                            || (value == bestValue && score > bestScore);
            if (better) {
                best = player.id();
                bestValue = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
