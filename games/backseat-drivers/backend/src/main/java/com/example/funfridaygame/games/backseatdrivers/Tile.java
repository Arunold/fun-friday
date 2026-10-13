package com.example.funfridaygame.games.backseatdrivers;

/**
 * The ground of one map cell. Hazards (cones, coffee, geese) and the start sit on paved tiles and
 * are kept separately in the {@link TrackMap}; the CEO's car is part of the ground because it never
 * moves.
 */
enum Tile {
    /** Drivable but slow (top speed {@link BusPhysics#OFFROAD_TOP_SPEED}). */
    GRASS('.', false, false),
    /** Solid. */
    BUILDING('#', true, false),
    /** Solid. */
    TREE('T', true, false),
    /** Solid. */
    WATER('w', true, false),
    /** Normal road. */
    ROAD('r', false, true),
    /** Road under rain clouds: the drivers' view blurs unless the wipers were tapped recently. */
    RAIN('~', false, true),
    /** Road in a tunnel: dark unless the headlights are on. */
    TUNNEL('t', false, true),
    /** The office parking lot: the bus centre on such a tile completes the level. */
    PARKING('P', false, true),
    /** The CEO's parked sports car: solid, and touching it ends the level. */
    CEO_CAR('E', true, false);

    private final char symbol;
    private final boolean solid;
    private final boolean paved;

    Tile(char symbol, boolean solid, boolean paved) {
        this.symbol = symbol;
        this.solid = solid;
        this.paved = paved;
    }

    /** The character used in map files and in the views' tile rows. */
    char symbol() {
        return symbol;
    }

    /** Whether the bus can't drive into the tile. */
    boolean solid() {
        return solid;
    }

    /** Whether the tile is a road surface (full speed). */
    boolean paved() {
        return paved;
    }

    /** The ground for a map character, or {@code null} if it isn't a ground character. */
    static Tile of(char symbol) {
        for (Tile tile : values()) {
            if (tile.symbol == symbol) {
                return tile;
            }
        }
        return null;
    }
}
