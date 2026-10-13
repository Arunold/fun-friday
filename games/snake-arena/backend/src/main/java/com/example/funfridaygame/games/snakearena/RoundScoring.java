package com.example.funfridaygame.games.snakearena;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** End-of-round bonuses and the reveal headline. */
final class RoundScoring {

    /** For the longest living snake(s) when the round ends (ties: all of them). */
    static final int LONGEST_BONUS = 5;

    /** For every snake still alive at the end of a last-snake-standing round. */
    static final int SURVIVOR_BONUS = 10;

    /**
     * Who earned a bonus.
     *
     * @param longest the longest living snakes
     * @param survivors the snakes alive at the end (last snake standing only)
     */
    record Summary(Set<String> longest, Set<String> survivors) {}

    private RoundScoring() {}

    /** Adds the bonuses to the snakes' round points. */
    static Summary applyBonuses(Collection<Snake> snakes, Variation variation) {
        List<Snake> alive = snakes.stream().filter(Snake::alive).toList();
        int longestLength = alive.stream().mapToInt(Snake::length).max().orElse(0);
        Set<String> longest = new LinkedHashSet<>();
        Set<String> survivors = new LinkedHashSet<>();
        for (Snake snake : alive) {
            if (snake.length() == longestLength) {
                snake.addPoints(LONGEST_BONUS);
                longest.add(snake.id());
            }
            if (variation == Variation.LAST_STANDING) {
                snake.addPoints(SURVIVOR_BONUS);
                survivors.add(snake.id());
            }
        }
        return new Summary(longest, survivors);
    }

    /**
     * "Dev ruled the arena with 31 points", "Ana and Ben shared the arena with 12 points each" or,
     * in last snake standing, "Ana survived!" / "Nobody survived!".
     */
    static String headline(
            Variation variation,
            Collection<Snake> snakes,
            Summary summary,
            Function<String, String> names) {
        if (variation == Variation.LAST_STANDING) {
            List<String> survivors = summary.survivors().stream().map(names).toList();
            return survivors.isEmpty() ? "Nobody survived!" : join(survivors) + " survived!";
        }
        int top = snakes.stream().mapToInt(Snake::points).max().orElse(0);
        if (top <= 0) {
            return "No points this round — the snakes just wandered";
        }
        List<String> winners =
                snakes.stream()
                        .filter(s -> s.points() == top)
                        .map(s -> names.apply(s.id()))
                        .toList();
        String points = top == 1 ? "1 point" : top + " points";
        return winners.size() == 1
                ? winners.getFirst() + " ruled the arena with " + points
                : join(winners) + " shared the arena with " + points + " each";
    }

    /** "Ana", "Ana and Ben", "Ana, Ben and Cleo". */
    static String join(List<String> names) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }
}
