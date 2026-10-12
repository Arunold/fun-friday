package com.example.funfridaygame.games.slidingpuzzle;

import com.example.funfridaygame.sdk.Award;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records across all rounds of a game, for the podium awards. */
final class SolveStats {

    private final Map<String, Long> fastestSolve = new LinkedHashMap<>();
    private final Map<String, Integer> fewestSolveMoves = new LinkedHashMap<>();
    private final Map<String, Integer> totalMoves = new LinkedHashMap<>();

    /** Adds a finished round of one player. */
    void record(String playerId, Attempt attempt) {
        if (attempt.moves() > 0) {
            totalMoves.merge(playerId, attempt.moves(), Integer::sum);
        }
        if (attempt.solved()) {
            fastestSolve.merge(playerId, attempt.solvedAfterMillis(), Math::min);
            fewestSolveMoves.merge(playerId, attempt.moves(), Math::min);
        }
    }

    /**
     * "Speed solver" (fastest solve), "Efficient" (fewest moves in a solve) and "Persistent" (most
     * moves overall). Ties go to the player who was recorded first.
     */
    List<Award> awards() {
        List<Award> awards = new ArrayList<>();
        fastestSolve.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "⚡",
                                                "Speed solver",
                                                e.getKey(),
                                                Scoring.formatTime(e.getValue()))));
        fewestSolveMoves.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🎯",
                                                "Efficient",
                                                e.getKey(),
                                                e.getValue() + " moves")));
        totalMoves.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "💪",
                                                "Persistent",
                                                e.getKey(),
                                                e.getValue() + " moves")));
        return awards;
    }
}
