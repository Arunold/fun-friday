package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.sdk.Award;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Light-hearted statistics for the podium awards, collected over the whole game:
 *
 * <ul>
 *   <li>🧭 <b>Best navigator</b>: guided the bus through the most new cells (cells it had never
 *       visited in that level) while navigating.
 *   <li>📯 <b>Horn hero</b>: scattered the most flocks of geese with the horn.
 *   <li>🦶 <b>Lead foot</b>: held the gas the longest (while it actually accelerated the bus).
 * </ul>
 *
 * Ties go to the player who joined first, so the result is deterministic.
 */
final class GameStats {

    /** A navigator needs at least this many new cells for the award. */
    static final int MIN_NAVIGATOR_CELLS = 10;

    /** The lead foot needs at least this much gas time. */
    static final long MIN_GAS_MILLIS = 3000;

    private final Map<String, Integer> navigatorCells = new HashMap<>();
    private final Map<String, Integer> geeseScattered = new HashMap<>();
    private final Map<String, Long> gasMillis = new HashMap<>();

    /** The bus entered a new cell while {@code navigatorId} was navigating. */
    void newCell(String navigatorId) {
        navigatorCells.merge(navigatorId, 1, Integer::sum);
    }

    /** A honk by these players scattered {@code flocks} flocks of geese. */
    void scattered(List<String> honkers, int flocks) {
        honkers.stream().distinct().forEach(id -> geeseScattered.merge(id, flocks, Integer::sum));
    }

    /** The player held the gas for {@code millis} while it acted. */
    void gas(String playerId, long millis) {
        gasMillis.merge(playerId, millis, Long::sum);
    }

    /**
     * The awards for the players still in the game.
     *
     * @param players player ids in join order
     */
    List<Award> awards(List<String> players) {
        List<Award> awards = new ArrayList<>();
        best(players, navigatorCells::get, MIN_NAVIGATOR_CELLS)
                .ifPresent(
                        id ->
                                awards.add(
                                        new Award(
                                                "🧭",
                                                "Best navigator",
                                                id,
                                                "Guided the bus through "
                                                        + navigatorCells.get(id)
                                                        + " new tiles")));
        best(players, geeseScattered::get, 1)
                .ifPresent(
                        id -> {
                            int flocks = geeseScattered.get(id);
                            awards.add(
                                    new Award(
                                            "📯",
                                            "Horn hero",
                                            id,
                                            "Scattered "
                                                    + flocks
                                                    + (flocks == 1 ? " flock" : " flocks")
                                                    + " of geese"));
                        });
        best(players, gasMillis::get, MIN_GAS_MILLIS)
                .ifPresent(
                        id ->
                                awards.add(
                                        new Award(
                                                "🦶",
                                                "Lead foot",
                                                id,
                                                "Held the gas for "
                                                        + Math.round(gasMillis.get(id) / 1000.0)
                                                        + " s")));
        return awards;
    }

    /** The player with the highest value of at least {@code minimum}; ties: the first. */
    private static Optional<String> best(
            List<String> players, Function<String, ? extends Number> value, long minimum) {
        String best = null;
        long bestValue = minimum - 1;
        for (String id : players) {
            Number number = value.apply(id);
            if (number != null && number.longValue() > bestValue) {
                best = id;
                bestValue = number.longValue();
            }
        }
        return Optional.ofNullable(best);
    }

    /** New cells guided as navigator (for tests and the debug state). */
    int navigatorCells(String playerId) {
        return navigatorCells.getOrDefault(playerId, 0);
    }

    /** Flocks scattered (for tests and the debug state). */
    int geeseScattered(String playerId) {
        return geeseScattered.getOrDefault(playerId, 0);
    }

    /** Gas time in milliseconds (for tests and the debug state). */
    long gasMillis(String playerId) {
        return gasMillis.getOrDefault(playerId, 0L);
    }
}
