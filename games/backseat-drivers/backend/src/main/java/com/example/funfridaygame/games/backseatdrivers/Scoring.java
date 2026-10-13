package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.LevelResult;

/**
 * Team scoring (GameIdeas.md): level score = seconds left × {@value #PER_SECOND} + coffees ×
 * {@value #PER_COFFEE} − cones × {@value #PER_CONE}, never below 0. Seconds left only count when
 * the bus parked; every player still in the game gets the same score.
 */
final class Scoring {

    /** Points per second left on the clock when parking. */
    static final int PER_SECOND = 10;

    /** Points per coffee cup. */
    static final int PER_COFFEE = 50;

    /** Points lost per cone. */
    static final int PER_CONE = 25;

    /** Level result: the bus reached the parking lot. */
    static final String PARKED = "PARKED";

    /** Level result: the bus touched the CEO's car. */
    static final String CRASHED = "CRASHED";

    /** Level result: the timer ran out. */
    static final String TIME_UP = "TIME_UP";

    private Scoring() {}

    /** The level score (at least 0). */
    static int levelScore(int secondsLeft, int coffees, int cones) {
        return Math.max(0, secondsLeft * PER_SECOND + coffees * PER_COFFEE - cones * PER_CONE);
    }

    /** Seconds left as the players see them on the timer (rounded up). */
    static int secondsLeft(long remainingMillis) {
        return (int) ((Math.max(0, remainingMillis) + 999) / 1000);
    }

    /** The reveal headline, e.g. "Parked with 34 s to spare! +540". */
    static String headline(LevelResult result) {
        return switch (result.status()) {
            case PARKED ->
                    "Parked with " + result.secondsLeft() + " s to spare! +" + result.score();
            case CRASHED -> "The CEO's car! Level over." + bonus(result);
            default -> "Out of time!" + bonus(result);
        };
    }

    private static String bonus(LevelResult result) {
        return result.score() > 0 ? " Coffee still pays: +" + result.score() : "";
    }
}
