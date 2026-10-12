package com.example.funfridaygame.games.reactionshowdown;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.Entry;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.SignalResult;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.TableRow;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sums up a round: points per player and the round table for the reveal screen (BUG-33). */
final class RoundTable {

    private RoundTable() {}

    /** Points per player id over the given signal results (false starts count negative). */
    static Map<String, Integer> points(List<SignalResult> results) {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (SignalResult result : results) {
            for (Entry entry : result.entries()) {
                points.merge(entry.playerId(), entry.points(), Integer::sum);
            }
        }
        return points;
    }

    /**
     * The round table: one row per player still in the game, best first (points, then best time).
     * Equal points share a rank.
     *
     * @param results the round's finished signals
     * @param players the players still in the game, in join order
     */
    static List<TableRow> of(List<SignalResult> results, List<PlayerInfo> players) {
        List<TableRow> rows = new ArrayList<>();
        for (PlayerInfo player : players) {
            rows.add(row(player.id(), results));
        }
        rows.sort(
                Comparator.comparingInt(TableRow::points)
                        .reversed()
                        .thenComparing(
                                TableRow::bestMs, Comparator.nullsLast(Comparator.naturalOrder())));
        List<TableRow> ranked = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            TableRow row = rows.get(i);
            if (i == 0 || row.points() != rows.get(i - 1).points()) {
                rank = i + 1;
            }
            ranked.add(
                    new TableRow(
                            row.playerId(),
                            rank,
                            row.bestMs(),
                            row.avgMs(),
                            row.points(),
                            row.falseStarts()));
        }
        return List.copyOf(ranked);
    }

    private static TableRow row(String playerId, List<SignalResult> results) {
        int points = 0;
        int falseStarts = 0;
        int count = 0;
        long sum = 0;
        Integer best = null;
        for (SignalResult result : results) {
            for (Entry entry : result.entries()) {
                if (!entry.playerId().equals(playerId)) {
                    continue;
                }
                points += entry.points();
                if (entry.falseStart()) {
                    falseStarts++;
                }
                if (entry.ms() != null) {
                    count++;
                    sum += entry.ms();
                    best = best == null ? entry.ms() : Math.min(best, entry.ms());
                }
            }
        }
        Integer avg = count == 0 ? null : (int) Math.round((double) sum / count);
        return new TableRow(playerId, 0, best, avg, points, falseStarts);
    }
}
