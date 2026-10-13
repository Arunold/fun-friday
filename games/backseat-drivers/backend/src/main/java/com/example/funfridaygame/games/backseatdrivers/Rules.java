package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.sdk.GameSettings;

/**
 * The host's settings in the form the logic uses.
 *
 * @param levels levels to drive (= rounds): 1 Campus Loop, 2 Downtown, 3 Business Park
 * @param levelSeconds the level timer
 * @param shuffleSeconds time between SEAT SHUFFLEs
 * @param variation classic, rush hour or silent navigator
 */
record Rules(int levels, int levelSeconds, int shuffleSeconds, Variation variation) {

    /** Reads validated settings. */
    static Rules from(GameSettings settings) {
        return new Rules(
                settings.getInt(GameSettings.ROUNDS),
                settings.getInt(GameSettings.ROUND_DURATION),
                settings.getInt(BackseatDriversGame.SHUFFLE),
                Variation.fromKey(settings.getString(BackseatDriversGame.VARIATION)));
    }

    /** The level timer in milliseconds. */
    long levelMillis() {
        return levelSeconds * 1000L;
    }

    /** Time between shuffles in milliseconds. */
    long shuffleMillis() {
        return shuffleSeconds * 1000L;
    }
}
