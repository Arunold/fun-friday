package com.example.funfridaygame.games.snakearena;

/** Power-ups and how many ticks they last once picked up. */
enum PowerType {
    /** Moves two cells per tick. */
    BOOST(30),
    /** Intangible for other snakes: passes through their bodies and they pass through it. */
    GHOST(30),
    /** Pulls food within {@value Simulation#MAGNET_RADIUS} cells one cell closer every tick. */
    MAGNET(50);

    private final int ticks;

    PowerType(int ticks) {
        this.ticks = ticks;
    }

    /** Duration in ticks. */
    int ticks() {
        return ticks;
    }
}
