package com.example.funfridaygame.games.reactionshowdown;

import java.util.random.RandomGenerator;

/**
 * The secret schedule of one signal: how long "Wait for it…" lasts and whether a fake-out flashes
 * before the real GO. Plans are drawn when a round starts and never leave the server before they
 * happen (BUG-31): views only ever show the state that is on screen right now.
 *
 * @param waitMillis length of the "Wait for it…" phase
 * @param fake the decoy shown after waiting, or {@code null} for a straight GO
 * @param fakeMillis how long the decoy stays on screen (0 without one)
 */
record SignalPlan(long waitMillis, FakeOut fake, long fakeMillis) {

    static final long MIN_WAIT_MS = 1500;
    static final long MAX_WAIT_MS = 4000;
    static final long MIN_FAKE_MS = 800;
    static final long MAX_FAKE_MS = 1500;

    /** Probability that a signal starts with a fake-out (when fake-outs are enabled). */
    static final double FAKE_CHANCE = 0.3;

    /**
     * Draws a random plan.
     *
     * @param random the game's random source
     * @param fakeouts whether fake-outs are enabled
     */
    static SignalPlan draw(RandomGenerator random, boolean fakeouts) {
        long wait = between(random, MIN_WAIT_MS, MAX_WAIT_MS);
        if (fakeouts && random.nextDouble() < FAKE_CHANCE) {
            FakeOut[] fakes = FakeOut.values();
            FakeOut fake = fakes[random.nextInt(fakes.length)];
            return new SignalPlan(wait, fake, between(random, MIN_FAKE_MS, MAX_FAKE_MS));
        }
        return new SignalPlan(wait, null, 0);
    }

    /** Whether a decoy flashes before the real GO. */
    boolean hasFake() {
        return fake != null;
    }

    /** The longest a signal can take before its GO window opens, as a constant upper bound. */
    static long maxLeadIn(boolean fakeouts) {
        return MAX_WAIT_MS + (fakeouts ? MAX_FAKE_MS : 0);
    }

    private static long between(RandomGenerator random, long min, long max) {
        return min + random.nextLong(max - min + 1);
    }
}
