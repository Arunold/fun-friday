package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Contact;
import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Handling;
import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Pedals;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Cell;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Flock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The world of one level: the bus on its map plus the hazards' state. Advanced one tick at a time
 * by {@link #step}; knows nothing about players or the platform.
 *
 * <ul>
 *   <li>Cones cost time and fly away; coffee cups give time and are drunk (the caller moves the
 *       deadline).
 *   <li>Geese start crossing when the bus comes within {@value #GEESE_TRIGGER} tiles and block
 *       their cells for {@value #GEESE_CROSSING_MS} ms; a honk within {@value #HORN_RANGE} tiles
 *       scatters them at once (also before they start).
 *   <li>Rain blurs the drivers' view unless the wipers were tapped in the last {@value
 *       #WIPE_CLEAR_MS} ms; tunnels are dark unless the headlights are on.
 * </ul>
 */
final class Simulation {

    /** Length of a tick in seconds. */
    static final double TICK_SECONDS = 0.05;

    /** A wipe keeps the windscreen clear this long. */
    static final long WIPE_CLEAR_MS = 1500;

    /** Geese start crossing when the bus is this close (tiles). */
    static final double GEESE_TRIGGER = 5.0;

    /** A honk scatters geese up to this far away (tiles). */
    static final double HORN_RANGE = 6.0;

    /** How long crossing geese block the road. */
    static final long GEESE_CROSSING_MS = 3000;

    /** Radius of a cone (tiles). */
    static final double CONE_RADIUS = 0.25;

    /** Radius of a coffee cup (tiles). */
    static final double COFFEE_RADIUS = 0.3;

    private static final long HONK_MS = 500;
    private static final long WIPE_ANIMATION_MS = 450;
    private static final long BUMP_MS = 200;
    private static final int TRAIL_EVERY_TICKS = 5;

    /** Effect bit: rain and no recent wipe — the drivers' view is blurred. */
    static final int BLUR = 1;

    /** Effect bit: in a tunnel without headlights — the drivers see almost nothing. */
    static final int DARK = 2;

    /** Effect bit: the horn sounds. */
    static final int HONK = 4;

    /** Effect bit: the headlights are on. */
    static final int LIGHTS = 8;

    /** Effect bit: the bus just bumped into something (screens shake). */
    static final int BUMP = 16;

    /** Effect bit: the bus scrapes along a wall. */
    static final int SCRAPE = 32;

    /** Effect bit: the wipers are wiping (animation). */
    static final int WIPE = 64;

    /** Effect bit: the bus is in the rain. */
    static final int RAIN = 128;

    /** Effect bit: the bus is in a tunnel. */
    static final int TUNNEL = 256;

    /** Effect bit: geese are crossing somewhere. */
    static final int GEESE = 512;

    /** Effect bit: the bus is off the road. */
    static final int OFFROAD = 1024;

    /** How the level is going. */
    enum Status {
        DRIVING,
        PARKED,
        CRASHED
    }

    /** State of a flock of geese. */
    enum GooseState {
        /** Waiting at the roadside. */
        IDLE,
        /** On the road, blocking it. */
        CROSSING,
        /** Crossed or scattered: harmless for the rest of the level. */
        GONE
    }

    /**
     * What one tick did.
     *
     * @param contact what the bus touched
     * @param conesHit cones hit this tick
     * @param coffees coffee cups collected this tick
     * @param geeseScattered flocks scattered by a honk this tick
     * @param hazardsChanged a cone, cup or flock changed (the drivers' window must be re-sent)
     * @param cellChanged the bus entered another cell
     * @param newCell the bus entered a cell for the first time this level
     */
    record Step(
            Contact contact,
            int conesHit,
            int coffees,
            int geeseScattered,
            boolean hazardsChanged,
            boolean cellChanged,
            boolean newCell) {}

    /**
     * Taps of utility controls during a tick.
     *
     * @param honk the horn sounded
     * @param wipe the wipers wiped
     * @param toggleLights the headlights were switched
     */
    record Taps(boolean honk, boolean wipe, boolean toggleLights) {

        /** No taps. */
        static final Taps NONE = new Taps(false, false, false);
    }

    private final TrackMap map;
    private final Bus bus;
    private final BitSet conesLeft;
    private final BitSet coffeesLeft;
    private final GooseState[] geese;
    private final long[] crossingUntil;
    private final BitSet visited;
    private final List<int[]> trail = new ArrayList<>();
    private boolean headlights;
    private long lastWipeAt = Long.MIN_VALUE / 2;
    private long honkUntil = Long.MIN_VALUE / 2;
    private long bumpUntil = Long.MIN_VALUE / 2;
    private boolean scraping;
    private int conesHit;
    private int coffees;
    private int ticks;
    private Status status = Status.DRIVING;

    /** A fresh level: the bus stands at the start, every hazard is in place. */
    Simulation(TrackMap map) {
        this.map = map;
        Cell start = map.start();
        this.bus = new Bus(start.centerX(), start.centerY(), map.heading().radians());
        this.conesLeft = new BitSet();
        conesLeft.set(0, map.cones().size());
        this.coffeesLeft = new BitSet();
        coffeesLeft.set(0, map.coffees().size());
        this.geese = new GooseState[map.geese().size()];
        Arrays.fill(geese, GooseState.IDLE);
        this.crossingUntil = new long[geese.length];
        this.visited = new BitSet(map.width() * map.height());
        visited.set(start.y() * map.width() + start.x());
        trail.add(point());
    }

    /**
     * Advances the world by one tick.
     *
     * @param pedals the drivers' pedals and steering
     * @param taps utility taps of this tick
     * @param handling current top speed and brake power
     * @param now current time (epoch ms)
     */
    Step step(Pedals pedals, Taps taps, Handling handling, long now) {
        if (status != Status.DRIVING) {
            return new Step(Contact.NONE, 0, 0, 0, false, false, false);
        }
        ticks++;
        int scattered = applyTaps(taps, now);
        boolean geeseMoved = updateGeese(now);
        boolean hazardsChanged = scattered > 0 || geeseMoved;
        int cellBefore = cellIndex();
        Contact contact = BusPhysics.step(bus, pedals, handling, TICK_SECONDS, ground());
        scraping = contact == Contact.SCRAPE;
        if (contact == Contact.BUMP) {
            bumpUntil = now + BUMP_MS;
        }
        if (contact == Contact.CEO) {
            status = Status.CRASHED;
        }
        int hitCones = collect(map.cones(), conesLeft, CONE_RADIUS);
        int drunk = collect(map.coffees(), coffeesLeft, COFFEE_RADIUS);
        conesHit += hitCones;
        coffees += drunk;
        hazardsChanged |= hitCones > 0 || drunk > 0;
        if (status == Status.DRIVING && map.tileAt(bus.x(), bus.y()) == Tile.PARKING) {
            status = Status.PARKED;
        }
        int cell = cellIndex();
        boolean cellChanged = cell != cellBefore;
        boolean newCell = cellChanged && !visited.get(cell);
        visited.set(cell);
        if (ticks % TRAIL_EVERY_TICKS == 0 || status != Status.DRIVING) {
            trail.add(point());
        }
        return new Step(contact, hitCones, drunk, scattered, hazardsChanged, cellChanged, newCell);
    }

    private int applyTaps(Taps taps, long now) {
        if (taps.toggleLights()) {
            headlights = !headlights;
        }
        if (taps.wipe()) {
            lastWipeAt = now;
        }
        if (!taps.honk()) {
            return 0;
        }
        honkUntil = now + HONK_MS;
        int scattered = 0;
        for (Flock flock : map.geese()) {
            if (geese[flock.index()] != GooseState.GONE
                    && flock.distanceTo(bus.x(), bus.y()) <= HORN_RANGE) {
                geese[flock.index()] = GooseState.GONE;
                scattered++;
            }
        }
        return scattered;
    }

    private boolean updateGeese(long now) {
        boolean changed = false;
        for (Flock flock : map.geese()) {
            int i = flock.index();
            if (geese[i] == GooseState.CROSSING && now >= crossingUntil[i]) {
                geese[i] = GooseState.GONE;
                changed = true;
            } else if (geese[i] == GooseState.IDLE
                    && flock.distanceTo(bus.x(), bus.y()) <= GEESE_TRIGGER) {
                geese[i] = GooseState.CROSSING;
                crossingUntil[i] = now + GEESE_CROSSING_MS;
                changed = true;
            }
        }
        return changed;
    }

    private int collect(List<Cell> cells, BitSet left, double radius) {
        int count = 0;
        for (int i = left.nextSetBit(0); i >= 0; i = left.nextSetBit(i + 1)) {
            Cell cell = cells.get(i);
            double distance = Math.hypot(cell.centerX() - bus.x(), cell.centerY() - bus.y());
            if (distance < Bus.RADIUS + radius) {
                left.clear(i);
                count++;
            }
        }
        return count;
    }

    private BusPhysics.Ground ground() {
        return new BusPhysics.Ground() {
            @Override
            public Tile tile(int x, int y) {
                return map.tile(x, y);
            }

            @Override
            public boolean blocked(int x, int y) {
                return map.tile(x, y).solid() || geeseOn(x, y);
            }
        };
    }

    /** Whether crossing geese block the cell. */
    boolean geeseOn(int x, int y) {
        for (Flock flock : map.geese()) {
            if (geese[flock.index()] == GooseState.CROSSING
                    && flock.cells().contains(new Cell(x, y))) {
                return true;
            }
        }
        return false;
    }

    /** The frame's effect bits at {@code now}. */
    int effects(long now) {
        Tile under = map.tileAt(bus.x(), bus.y());
        int bits = 0;
        if (under == Tile.RAIN) {
            bits |= RAIN;
            if (now - lastWipeAt > WIPE_CLEAR_MS) {
                bits |= BLUR;
            }
        }
        if (under == Tile.TUNNEL) {
            bits |= TUNNEL;
            if (!headlights) {
                bits |= DARK;
            }
        }
        if (now < honkUntil) {
            bits |= HONK;
        }
        if (headlights) {
            bits |= LIGHTS;
        }
        if (now < bumpUntil) {
            bits |= BUMP;
        }
        if (scraping) {
            bits |= SCRAPE;
        }
        if (now - lastWipeAt < WIPE_ANIMATION_MS) {
            bits |= WIPE;
        }
        if (crossingMask() != 0) {
            bits |= GEESE;
        }
        if (!under.paved()) {
            bits |= OFFROAD;
        }
        return bits;
    }

    /** Bit {@code i} set = flock {@code i} is crossing. */
    int crossingMask() {
        int mask = 0;
        for (int i = 0; i < geese.length; i++) {
            if (geese[i] == GooseState.CROSSING) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private int cellIndex() {
        return Math.clamp(bus.cellY(), 0, map.height() - 1) * map.width()
                + Math.clamp(bus.cellX(), 0, map.width() - 1);
    }

    private int[] point() {
        return new int[] {(int) Math.round(bus.x() * 100), (int) Math.round(bus.y() * 100)};
    }

    TrackMap map() {
        return map;
    }

    Bus bus() {
        return bus;
    }

    Status status() {
        return status;
    }

    int conesHit() {
        return conesHit;
    }

    int coffees() {
        return coffees;
    }

    boolean headlights() {
        return headlights;
    }

    /** Whether cone {@code i} is still on the road. */
    boolean coneLeft(int i) {
        return conesLeft.get(i);
    }

    /** Whether coffee cup {@code i} is still on the road. */
    boolean coffeeLeft(int i) {
        return coffeesLeft.get(i);
    }

    /** State of flock {@code i}. */
    GooseState goose(int i) {
        return geese[i];
    }

    /** The bus's path so far: points {@code [x·100, y·100]}. */
    List<int[]> trail() {
        return List.copyOf(trail);
    }
}
