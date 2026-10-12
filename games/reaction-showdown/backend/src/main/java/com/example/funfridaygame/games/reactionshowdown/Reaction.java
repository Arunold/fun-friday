package com.example.funfridaygame.games.reactionshowdown;

/**
 * How one player reacted to one signal: a valid reaction time or a false start.
 *
 * @param ms the accepted reaction time in milliseconds ({@code null} for false starts)
 * @param foul why the tap was a false start ({@code null} for valid taps)
 */
record Reaction(Integer ms, Foul foul) {

    /** Kinds of false start. */
    enum Foul {
        /** Tapped while waiting, or so fast that it can only have been a guess (anticipation). */
        EARLY,
        /** Tapped the decoy colour. */
        FAKE
    }

    /** A valid tap after GO. */
    static Reaction valid(int ms) {
        return new Reaction(ms, null);
    }

    /** A false start. */
    static Reaction falseStart(Foul foul) {
        return new Reaction(null, foul);
    }

    /** Whether the tap counts (no false start). */
    boolean isValid() {
        return foul == null;
    }

    /** Whether the player fell for a fake-out. */
    boolean onFake() {
        return foul == Foul.FAKE;
    }
}
