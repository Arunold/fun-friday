package com.example.funfridaygame.games.reactionshowdown;

/**
 * The decoys that may flash before the real GO. Each one is a sudden, bright colour that is not
 * green — tapping it is a false start. The frontend maps the names to a colour and a label.
 */
enum FakeOut {
    /** Red "✋ NOT YET". */
    HOLD,
    /** Amber "🤚 STEADY…". */
    STEADY,
    /** Violet "🧊 FREEZE!". */
    FREEZE
}
