package com.example.funfridaygame.games.backseatdrivers;

/**
 * Everything a seat on the bus can hold. The navigator role is listed too, so a seat is simply a
 * list of controls.
 *
 * <p>{@link #bit()} is used in the compact frames: the pressed controls of a seat are one integer.
 */
enum Control {
    /** Sees the whole map and shouts directions; presses nothing. */
    NAVIGATOR(Kind.ROLE),
    /** Turns the bus to the left while held. */
    STEER_LEFT(Kind.HOLD),
    /** Turns the bus to the right while held. */
    STEER_RIGHT(Kind.HOLD),
    /** Accelerates while held. */
    GAS(Kind.HOLD),
    /** Brakes while held; reverses slowly once the bus stands still. */
    BRAKE(Kind.HOLD),
    /** A tap honks (scatters geese). */
    HORN(Kind.TAP),
    /** A tap wipes the windscreen once (clears the rain blur for a moment). */
    WIPERS(Kind.TAP),
    /** A tap switches the headlights on or off (lights up tunnels). */
    HEADLIGHTS(Kind.TAP);

    /** How a control is operated. */
    enum Kind {
        /** Not a control at all: the navigator. */
        ROLE,
        /** Works while the key is held. */
        HOLD,
        /** Works once per key press. */
        TAP
    }

    private final Kind kind;

    Control(Kind kind) {
        this.kind = kind;
    }

    /** The control's bit in a pressed-controls mask. */
    int bit() {
        return 1 << ordinal();
    }

    /** Whether the control works while held (steering and pedals). */
    boolean isHold() {
        return kind == Kind.HOLD;
    }

    /** Whether the control works per tap: the utility controls horn, wipers and headlights. */
    boolean isTap() {
        return kind == Kind.TAP;
    }
}
