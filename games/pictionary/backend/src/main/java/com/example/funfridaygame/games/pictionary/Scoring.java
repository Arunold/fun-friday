package com.example.funfridaygame.games.pictionary;

/**
 * Points of a turn (BUG-19: the reveal shows exactly what is awarded here).
 *
 * <table>
 *   <caption>Scoring</caption>
 *   <tr><th>Who</th><th>Points</th></tr>
 *   <tr><td>Correct guesser</td><td>100, falling linearly to 40 at the end of the timer</td></tr>
 *   <tr><td>First correct guesser</td><td>+20</td></tr>
 *   <tr><td>Drawer</td><td>25 per correct guesser, at most 150</td></tr>
 * </table>
 */
final class Scoring {

    /** Points for a guess at the very start of the drawing phase. */
    static final int GUESS_MAX = 100;

    /** Points for a guess at the very end. */
    static final int GUESS_MIN = 40;

    /** Extra points for the first correct guess. */
    static final int FIRST_BONUS = 20;

    /** Drawer points per correct guesser. */
    static final int DRAWER_PER_GUESS = 25;

    /** Most points a drawer can earn in a turn. */
    static final int DRAWER_MAX = 150;

    private Scoring() {}

    /**
     * Points for a correct guess.
     *
     * @param elapsedMillis time since the drawing phase started
     * @param drawMillis length of the drawing phase
     * @param first whether nobody guessed before
     */
    static int guesser(long elapsedMillis, long drawMillis, boolean first) {
        double left = drawMillis <= 0 ? 0 : 1.0 - (double) elapsedMillis / drawMillis;
        double share = Math.clamp(left, 0.0, 1.0);
        int points = GUESS_MIN + (int) Math.round((GUESS_MAX - GUESS_MIN) * share);
        return first ? points + FIRST_BONUS : points;
    }

    /** Points for the drawer when {@code correctGuessers} players got the word. */
    static int drawer(int correctGuessers) {
        return Math.min(DRAWER_MAX, DRAWER_PER_GUESS * correctGuessers);
    }
}
