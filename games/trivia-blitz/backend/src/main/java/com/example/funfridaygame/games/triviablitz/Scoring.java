package com.example.funfridaygame.games.triviablitz;

/**
 * Points for a correct answer: speed sets the base (1000 for an instant answer, sliding linearly to
 * 500 at the deadline, rounded to 10) and consecutive correct answers add a streak bonus (+100 per
 * correct answer in a row beyond the first, at most +300). Wrong or missing answers score 0.
 */
final class Scoring {

    /** Base points for an instant correct answer. */
    static final int FASTEST = 1000;

    /** Base points for a correct answer at the very last moment. */
    static final int SLOWEST = 500;

    /** Bonus per correct answer in a row beyond the first. */
    static final int STREAK_STEP = 100;

    /** Largest streak bonus. */
    static final int MAX_STREAK_BONUS = 300;

    private Scoring() {}

    /**
     * Total points for a correct answer.
     *
     * @param elapsedMillis time from the start of the question to the answer
     * @param durationMillis length of the question's timer
     * @param streak correct answers in a row, including this one (at least 1)
     */
    static int points(long elapsedMillis, long durationMillis, int streak) {
        return speedPoints(elapsedMillis, durationMillis) + streakBonus(streak);
    }

    /** 1000 for an instant answer down to 500 at the deadline, rounded to the nearest 10. */
    static int speedPoints(long elapsedMillis, long durationMillis) {
        double share =
                durationMillis <= 0
                        ? 1.0
                        : Math.clamp((double) elapsedMillis / durationMillis, 0.0, 1.0);
        double raw = FASTEST - (FASTEST - SLOWEST) * share;
        return (int) Math.round(raw / 10) * 10;
    }

    /** +100 for every correct answer in a row beyond the first, at most +300. */
    static int streakBonus(int streak) {
        return Math.min(MAX_STREAK_BONUS, STREAK_STEP * Math.max(0, streak - 1));
    }
}
