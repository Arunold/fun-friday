package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.sdk.Award;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Game-wide statistics behind the podium awards. */
final class GameStats {

    private final Map<String, Integer> guessedDrawings = new LinkedHashMap<>();
    private final Map<String, Integer> firstGuesses = new LinkedHashMap<>();
    private final Map<String, Long> fastestMillis = new LinkedHashMap<>();

    /** Records a scored turn: who drew and who guessed in which order and how fast. */
    void recordTurn(String drawerId, List<GuessBook.Correct> correct) {
        if (correct.isEmpty()) {
            return;
        }
        guessedDrawings.merge(drawerId, correct.size(), Integer::sum);
        firstGuesses.merge(correct.getFirst().playerId(), 1, Integer::sum);
        for (GuessBook.Correct guess : correct) {
            fastestMillis.merge(guess.playerId(), guess.elapsedMillis(), Math::min);
        }
    }

    /**
     * Picasso (drawings guessed by the most players), Mind reader (most first guesses) and Quick
     * eye (fastest correct guess). Ties go to whoever reached the value first.
     */
    List<Award> awards() {
        List<Award> awards = new ArrayList<>();
        highest(guessedDrawings)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🎨",
                                                "Picasso",
                                                e.getKey(),
                                                plural(
                                                                e.getValue(),
                                                                "correct guess",
                                                                "correct guesses")
                                                        + " on their drawings")));
        highest(firstGuesses)
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "🔮",
                                                "Mind reader",
                                                e.getKey(),
                                                "Guessed first "
                                                        + plural(e.getValue(), "time", "times"))));
        fastestMillis.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .ifPresent(
                        e ->
                                awards.add(
                                        new Award(
                                                "👀",
                                                "Quick eye",
                                                e.getKey(),
                                                String.format(
                                                        Locale.ROOT,
                                                        "Guessed a drawing in %.1f s",
                                                        e.getValue() / 1000.0))));
        return awards;
    }

    private static Optional<Map.Entry<String, Integer>> highest(Map<String, Integer> values) {
        Map.Entry<String, Integer> best = null;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    private static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
