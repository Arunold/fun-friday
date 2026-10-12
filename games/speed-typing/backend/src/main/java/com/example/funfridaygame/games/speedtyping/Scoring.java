package com.example.funfridaygame.games.speedtyping;

/**
 * Points of a turn.
 *
 * <ul>
 *   <li>Finishers, by finishing place: 100 / 80 / 65 / 55, then 50 for everyone after that;
 *   <li>plus a speed bonus of WPM ÷ 4 (at most 30, reached at 120 WPM);
 *   <li>times the accuracy (96 % → × 0.96), and halved when the accuracy is below the minimum;
 *   <li>players who didn't finish: up to 40 by progress (correctly typed characters).
 * </ul>
 *
 * <p>Below the minimum accuracy a finish still counts (place, time, WPM) — it is simply worth half,
 * which keeps the rule easy to explain and never leaves a player stuck at the end of the text.
 */
final class Scoring {

    /** Points by finishing place; places after the last entry get the last entry. */
    static final int[] PLACE_POINTS = {100, 80, 65, 55, 50};

    /** The speed bonus stops growing at this WPM. */
    static final int WPM_BONUS_CAP = 120;

    /** WPM per bonus point. */
    static final int WPM_PER_POINT = 4;

    /** Most points for a player who didn't finish. */
    static final int PROGRESS_POINTS = 40;

    /** Factor for finishers below the minimum accuracy. */
    static final double BELOW_MINIMUM_FACTOR = 0.5;

    private Scoring() {}

    /** Points for a finishing place (1 = first). */
    static int placePoints(int rank) {
        int index = Math.min(Math.max(rank, 1), PLACE_POINTS.length) - 1;
        return PLACE_POINTS[index];
    }

    /** Speed bonus for a typing speed. */
    static double speedBonus(double wpm) {
        return Math.min(Math.max(wpm, 0), WPM_BONUS_CAP) / WPM_PER_POINT;
    }

    /**
     * Points of a finisher.
     *
     * @param rank finishing place, 1 = first
     * @param wpm typing speed
     * @param accuracy correct keystrokes ÷ all keystrokes (0–1)
     * @param belowMinimum whether the accuracy is below the game's minimum
     */
    static int finisher(int rank, double wpm, double accuracy, boolean belowMinimum) {
        double factor = Math.min(Math.max(accuracy, 0), 1);
        if (belowMinimum) {
            factor *= BELOW_MINIMUM_FACTOR;
        }
        return (int) Math.round((placePoints(rank) + speedBonus(wpm)) * factor);
    }

    /** Points of a player who didn't finish: a share of {@value #PROGRESS_POINTS} by progress. */
    static int unfinished(int correctChars, int textLength) {
        if (textLength <= 0) {
            return 0;
        }
        double share = Math.min(correctChars, textLength) / (double) textLength;
        return (int) Math.round(PROGRESS_POINTS * share);
    }
}
