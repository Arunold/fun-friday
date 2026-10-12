package com.example.funfridaygame.games.triviablitz;

import java.util.OptionalDouble;

/** One player's running record over a game: correct answers, their speed and streaks. */
final class PlayerRecord {

    private int correct;
    private long correctMillis;
    private int streak;
    private int bestStreak;

    /**
     * Counts a correct answer.
     *
     * @param elapsedMillis how long the player took to answer
     * @return the streak including this answer
     */
    int recordCorrect(long elapsedMillis) {
        correct++;
        correctMillis += elapsedMillis;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        return streak;
    }

    /**
     * Counts a wrong or missing answer: the streak starts over.
     *
     * @return the streak that was lost
     */
    int recordMiss() {
        int lost = streak;
        streak = 0;
        return lost;
    }

    /** Correct answers so far. */
    int correct() {
        return correct;
    }

    /** Current run of correct answers. */
    int streak() {
        return streak;
    }

    /** Longest run of correct answers in this game. */
    int bestStreak() {
        return bestStreak;
    }

    /** Average time of the correct answers, empty without any. */
    OptionalDouble averageCorrectMillis() {
        return correct == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) correctMillis / correct);
    }
}
