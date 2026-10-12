package com.example.funfridaygame.runtime;

import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Final result of a game run.
 *
 * @param standings players ordered by score; ties share a rank (1, 1, 3)
 * @param awards fun titles from the game
 * @param history every turn's points
 * @param startedAt when the first turn started
 * @param finishedAt when the podium was reached
 */
public record GameResult(
        List<Standing> standings,
        List<Award> awards,
        List<TurnRecord> history,
        Instant startedAt,
        Instant finishedAt) {

    public GameResult {
        standings = List.copyOf(standings);
        awards = List.copyOf(awards);
        history = List.copyOf(history);
    }

    /**
     * One line of the final ranking.
     *
     * @param player the player
     * @param score final score
     * @param rank 1-based rank; equal scores share a rank
     */
    public record Standing(PlayerInfo player, int score, int rank) {}

    /** Ranks players by score (descending) with standard competition ranking for ties. */
    public static List<Standing> rank(List<PlayerInfo> players, Map<String, Integer> scores) {
        List<PlayerInfo> ordered = new ArrayList<>(players);
        ordered.sort(
                Comparator.comparingInt((PlayerInfo p) -> scores.getOrDefault(p.id(), 0))
                        .reversed());
        List<Standing> standings = new ArrayList<>();
        int previousScore = Integer.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < ordered.size(); i++) {
            PlayerInfo player = ordered.get(i);
            int score = scores.getOrDefault(player.id(), 0);
            if (score != previousScore) {
                rank = i + 1;
                previousScore = score;
            }
            standings.add(new Standing(player, score, rank));
        }
        return standings;
    }
}
