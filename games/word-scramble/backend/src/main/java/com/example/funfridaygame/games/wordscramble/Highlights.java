package com.example.funfridaygame.games.wordscramble;

import com.example.funfridaygame.sdk.Award;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Remembers every solve of the game and turns them into podium awards: ⚡ Speed reader (fastest
 * single solve), 📚 Wordsmith (most first places) and 💪 Never give up (most wrong guesses before
 * cracking a word, at least {@value #MIN_MISSES_FOR_GRIT}). Only players still in the game qualify.
 */
final class Highlights {

    /** Misses needed before a solve counts as "never give up". */
    static final int MIN_MISSES_FOR_GRIT = 3;

    private final List<Solve> solves = new ArrayList<>();

    /**
     * One solve.
     *
     * @param playerId who solved
     * @param word the word
     * @param rank solve order in its round
     * @param millis time from the round start
     * @param misses wrong guesses before the solve
     */
    record Solve(String playerId, String word, int rank, long millis, int misses) {}

    /** Records a solve. */
    void add(Solve solve) {
        solves.add(solve);
    }

    /** The awards among the given players (in a stable order). */
    List<Award> awards(Set<String> players) {
        List<Solve> eligible = solves.stream().filter(s -> players.contains(s.playerId())).toList();
        List<Award> awards = new ArrayList<>();
        fastest(eligible).ifPresent(awards::add);
        mostFirsts(eligible).ifPresent(awards::add);
        mostPersistent(eligible).ifPresent(awards::add);
        return awards;
    }

    private static Optional<Award> fastest(List<Solve> solves) {
        return solves.stream()
                .min(Comparator.comparingLong(Solve::millis))
                .map(
                        s ->
                                new Award(
                                        "⚡",
                                        "Speed reader",
                                        s.playerId(),
                                        s.word() + " in " + seconds(s.millis())));
    }

    private static Optional<Award> mostFirsts(List<Solve> solves) {
        Map<String, Integer> firsts = new LinkedHashMap<>();
        solves.stream()
                .filter(s -> s.rank() == 1)
                .forEach(s -> firsts.merge(s.playerId(), 1, Integer::sum));
        return firsts.entrySet().stream()
                .reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
                .map(
                        e ->
                                new Award(
                                        "📚",
                                        "Wordsmith",
                                        e.getKey(),
                                        e.getValue()
                                                + (e.getValue() == 1
                                                        ? " first solve"
                                                        : " first solves")));
    }

    private static Optional<Award> mostPersistent(List<Solve> solves) {
        return solves.stream()
                .filter(s -> s.misses() >= MIN_MISSES_FOR_GRIT)
                .reduce((best, next) -> next.misses() > best.misses() ? next : best)
                .map(
                        s ->
                                new Award(
                                        "💪",
                                        "Never give up",
                                        s.playerId(),
                                        s.word() + " after " + s.misses() + " misses"));
    }

    /** Formats milliseconds as e.g. {@code 4.2 s}. */
    static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
