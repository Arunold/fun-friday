package com.example.funfridaygame.games.triviablitz;

import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The podium awards of a game:
 *
 * <ul>
 *   <li>🧠 <b>Brainiac</b> — most correct answers (ties: faster average answer);
 *   <li>⚡ <b>Quick draw</b> — fastest average correct answer (ties: more correct answers);
 *   <li>🔥 <b>On fire</b> — longest streak, at least 2 (ties: more correct answers).
 * </ul>
 *
 * <p>Remaining ties go to the player who joined first. Only players still in the game qualify.
 */
final class TriviaAwards {

    private TriviaAwards() {}

    /**
     * Computes the awards.
     *
     * @param players players still in the game, in join order
     * @param records every player's record by id
     * @param asked how many questions were asked
     */
    static List<Award> of(List<PlayerInfo> players, Map<String, PlayerRecord> records, int asked) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            PlayerRecord record = records.get(players.get(i).id());
            if (record != null && record.correct() > 0) {
                entries.add(new Entry(players.get(i).id(), i, record));
            }
        }
        List<Award> awards = new ArrayList<>();
        brainiac(entries)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🧠",
                                                "Brainiac",
                                                e.playerId(),
                                                e.record().correct()
                                                        + " of "
                                                        + asked
                                                        + " correct")));
        quickDraw(entries)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "⚡",
                                                "Quick draw",
                                                e.playerId(),
                                                seconds(e.averageMillis()) + " on average")));
        onFire(entries)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🔥",
                                                "On fire",
                                                e.playerId(),
                                                e.record().bestStreak() + " in a row")));
        return awards;
    }

    private static Optional<Entry> brainiac(List<Entry> entries) {
        return entries.stream()
                .min(
                        Comparator.comparingInt((Entry e) -> -e.record().correct())
                                .thenComparingDouble(Entry::averageMillis)
                                .thenComparingInt(Entry::seat));
    }

    private static Optional<Entry> quickDraw(List<Entry> entries) {
        return entries.stream()
                .min(
                        Comparator.comparingDouble(Entry::averageMillis)
                                .thenComparingInt(e -> -e.record().correct())
                                .thenComparingInt(Entry::seat));
    }

    private static Optional<Entry> onFire(List<Entry> entries) {
        return entries.stream()
                .filter(e -> e.record().bestStreak() >= 2)
                .min(
                        Comparator.comparingInt((Entry e) -> -e.record().bestStreak())
                                .thenComparingInt(e -> -e.record().correct())
                                .thenComparingInt(Entry::seat));
    }

    private static String seconds(double millis) {
        return String.format(Locale.ROOT, "%.1f s", millis / 1000);
    }

    /** A player with at least one correct answer; {@code seat} is the join order. */
    private record Entry(String playerId, int seat, PlayerRecord record) {
        double averageMillis() {
            return record.averageCorrectMillis().orElse(Double.MAX_VALUE);
        }
    }
}
