package com.example.funfridaygame.games.speedtyping;

import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What every player achieved over the whole game, for the podium awards:
 *
 * <ul>
 *   <li>⚡ Fastest fingers — the best WPM of a finished round;
 *   <li>🎯 Sharpshooter — the best average accuracy (among players who finished a round);
 *   <li>🏃 Marathon — the most characters typed, corrections included.
 * </ul>
 */
final class GameStats {

    private final Map<String, PlayerStats> byPlayer = new HashMap<>();

    /** One player's totals. */
    private static final class PlayerStats {
        private double bestWpm;
        private int finishes;
        private int typedRounds;
        private double accuracySum;
        private long keystrokes;

        double averageAccuracy() {
            return typedRounds == 0 ? 0 : accuracySum / typedRounds;
        }
    }

    /** Adds a player's finished turn (called once per turn and player). */
    void record(String playerId, Racer racer, double wpm) {
        if (!racer.typedAnything()) {
            return;
        }
        PlayerStats stats = byPlayer.computeIfAbsent(playerId, id -> new PlayerStats());
        stats.typedRounds++;
        stats.accuracySum += racer.accuracy();
        stats.keystrokes += racer.keystrokes();
        if (racer.finished()) {
            stats.finishes++;
            stats.bestWpm = Math.max(stats.bestWpm, wpm);
        }
    }

    /** The awards among the players still in the game (in join order for ties). */
    List<Award> awards(List<PlayerInfo> players) {
        List<Award> awards = new ArrayList<>();
        best(players, s -> s.finishes > 0, Comparator.comparingDouble(s -> s.bestWpm))
                .ifPresent(
                        id ->
                                awards.add(
                                        new Award(
                                                "⚡",
                                                "Fastest fingers",
                                                id,
                                                Math.round(byPlayer.get(id).bestWpm) + " WPM")));
        Comparator<PlayerStats> accuracy =
                Comparator.<PlayerStats>comparingDouble(PlayerStats::averageAccuracy)
                        .thenComparingLong(s -> s.keystrokes);
        best(players, s -> s.finishes > 0, accuracy)
                .ifPresent(
                        id ->
                                awards.add(
                                        new Award(
                                                "🎯",
                                                "Sharpshooter",
                                                id,
                                                percent(byPlayer.get(id).averageAccuracy())
                                                        + " average accuracy")));
        best(players, s -> s.keystrokes > 0, Comparator.comparingLong(s -> s.keystrokes))
                .ifPresent(
                        id ->
                                awards.add(
                                        new Award(
                                                "🏃",
                                                "Marathon",
                                                id,
                                                String.format(
                                                        Locale.ROOT,
                                                        "%,d characters typed",
                                                        byPlayer.get(id).keystrokes))));
        return awards;
    }

    /** The first player (join order) with the highest stats among the eligible ones. */
    private Optional<String> best(
            List<PlayerInfo> players,
            Predicate<PlayerStats> eligible,
            Comparator<PlayerStats> order) {
        String bestId = null;
        PlayerStats bestStats = null;
        for (PlayerInfo player : players) {
            PlayerStats stats = byPlayer.get(player.id());
            if (stats != null
                    && eligible.test(stats)
                    && (bestStats == null || order.compare(stats, bestStats) > 0)) {
                bestId = player.id();
                bestStats = stats;
            }
        }
        return Optional.ofNullable(bestId);
    }

    private static String percent(double fraction) {
        return (int) Math.floor(fraction * 100 + 1e-9) + "%";
    }
}
