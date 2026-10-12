package com.example.funfridaygame.games.reactionshowdown;

/** What the field shows during one signal. Sent to browsers as {@code state}. */
enum SignalState {
    /** "Wait for it…" — tapping now is a false start. */
    WAITING,
    /** A decoy colour — tapping now is a false start too. */
    FAKE,
    /** Green: tap! Open until everyone reacted or the GO window closes. */
    GO,
    /** The signal is scored; its mini-result is shown before the next signal. */
    RESULT;

    /** Whether taps are accepted (and judged) in this state. */
    boolean acceptsTaps() {
        return this != RESULT;
    }
}
