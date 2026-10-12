package com.example.funfridaygame.games.reactionshowdown;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.Entry;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.SignalResult;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Game-wide statistics behind the podium awards: every valid reaction time and every false start of
 * every finished signal.
 */
final class GameStats {

    /** Valid taps needed to qualify for "Steady hands" (if anyone has that many). */
    static final int STEADY_MIN_TAPS = 2;

    private final Map<String, List<Integer>> times = new HashMap<>();
    private final Map<String, Integer> falseStarts = new HashMap<>();

    /** Adds a finished signal. */
    void record(SignalResult result) {
        for (Entry entry : result.entries()) {
            if (entry.ms() != null) {
                times.computeIfAbsent(entry.playerId(), id -> new ArrayList<>()).add(entry.ms());
            } else if (entry.falseStart()) {
                falseStarts.merge(entry.playerId(), 1, Integer::sum);
            }
        }
    }

    /**
     * The awards among the players still in the game (ties go to the earlier player in join order).
     *
     * <ul>
     *   <li>⚡ Lightning — fastest single reaction
     *   <li>🎯 Steady hands — best average reaction ({@value #STEADY_MIN_TAPS}+ valid taps when
     *       anyone has that many)
     *   <li>🐇 Jumpy — most false starts
     * </ul>
     */
    List<Award> awards(List<PlayerInfo> players) {
        List<Award> awards = new ArrayList<>();
        lightning(players, awards);
        steadyHands(players, awards);
        jumpy(players, awards);
        return awards;
    }

    private void lightning(List<PlayerInfo> players, List<Award> awards) {
        String best = null;
        int bestMs = Integer.MAX_VALUE;
        for (PlayerInfo player : players) {
            for (int ms : times.getOrDefault(player.id(), List.of())) {
                if (ms < bestMs) {
                    bestMs = ms;
                    best = player.id();
                }
            }
        }
        if (best != null) {
            awards.add(new Award("⚡", "Lightning", best, bestMs + " ms"));
        }
    }

    private void steadyHands(List<PlayerInfo> players, List<Award> awards) {
        boolean anyoneQualifies =
                players.stream()
                        .anyMatch(
                                p ->
                                        times.getOrDefault(p.id(), List.of()).size()
                                                >= STEADY_MIN_TAPS);
        int minTaps = anyoneQualifies ? STEADY_MIN_TAPS : 1;
        String best = null;
        double bestAverage = Double.MAX_VALUE;
        for (PlayerInfo player : players) {
            List<Integer> mine = times.getOrDefault(player.id(), List.of());
            if (mine.size() < minTaps) {
                continue;
            }
            double average = mine.stream().mapToInt(Integer::intValue).average().orElseThrow();
            if (average < bestAverage) {
                bestAverage = average;
                best = player.id();
            }
        }
        if (best != null) {
            awards.add(
                    new Award("🎯", "Steady hands", best, "⌀ " + Math.round(bestAverage) + " ms"));
        }
    }

    private void jumpy(List<PlayerInfo> players, List<Award> awards) {
        String jumpiest = null;
        int most = 0;
        for (PlayerInfo player : players) {
            int count = falseStarts.getOrDefault(player.id(), 0);
            if (count > most) {
                most = count;
                jumpiest = player.id();
            }
        }
        if (jumpiest != null) {
            awards.add(
                    new Award(
                            "🐇",
                            "Jumpy",
                            jumpiest,
                            most + (most == 1 ? " false start" : " false starts")));
        }
    }
}
