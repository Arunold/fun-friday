package com.example.funfridaygame.games.backseatdrivers;

/**
 * Simple arcade physics for the shuttle: pedals change the speed, steering turns it in proportion
 * to the speed, and the move is checked against the ground (circle against tiles).
 *
 * <ul>
 *   <li>GAS accelerates at {@value #ACCELERATION} tiles/s² up to the top speed ({@value #TOP_SPEED}
 *       on road, {@value #OFFROAD_TOP_SPEED} on grass, where extra drag slows a fast bus down).
 *   <li>BRAKE decelerates (9 tiles/s²) and, once stopped, reverses slowly (at most {@value
 *       #REVERSE_TOP_SPEED}). BRAKE wins over GAS. Without a pedal the bus rolls out.
 *   <li>Steering turns at up to {@value #TURN_RATE} rad/s, scaled by the speed (no turning while
 *       stopped) and mirrored when reversing, like a real car.
 *   <li>Hitting a solid tile undoes the move and stops the bus (a bump); at a glancing angle it
 *       slides along instead and loses some speed (a scrape). Touching the CEO's car is a crash.
 * </ul>
 */
final class BusPhysics {

    /** Acceleration with GAS (tiles/s²). */
    static final double ACCELERATION = 4.0;

    /** Top speed on road (tiles/s). */
    static final double TOP_SPEED = 6.0;

    /** Rush hour: top speed at the end of the level timer. */
    static final double RUSH_TOP_SPEED = 8.0;

    /** Top speed on grass. */
    static final double OFFROAD_TOP_SPEED = 1.5;

    /** Extra deceleration while faster than the top speed (driving onto grass). */
    static final double OFFROAD_DRAG = 10.0;

    /** Deceleration with BRAKE. */
    static final double BRAKE = 9.0;

    /** Rush hour: brake power at the end of the level timer. */
    static final double RUSH_BRAKE = 4.5;

    /** Acceleration backwards with BRAKE once stopped. */
    static final double REVERSE_ACCELERATION = 3.0;

    /** Top speed backwards. */
    static final double REVERSE_TOP_SPEED = 1.5;

    /** Deceleration without pedals. */
    static final double ROLLING_FRICTION = 2.0;

    /** Turn rate at full steering (rad/s). */
    static final double TURN_RATE = 2.6;

    /** From this speed on steering works at its full rate; below it proportionally less. */
    static final double FULL_STEER_SPEED = 2.0;

    /** Speed kept per tick while scraping along a wall. */
    static final double SCRAPE_FACTOR = 0.94;

    /**
     * A blocked move slides along the wall if at least this share of it is parallel to the wall.
     */
    static final double MIN_SLIDE = 0.5;

    private BusPhysics() {}

    /**
     * Speed limits of the bus at one moment.
     *
     * @param topSpeed top speed on road (tiles/s)
     * @param brake deceleration with BRAKE (tiles/s²)
     */
    record Handling(double topSpeed, double brake) {

        /** The normal bus. */
        static Handling classic() {
            return new Handling(TOP_SPEED, BRAKE);
        }

        /**
         * Rush hour: the top speed rises and the brakes weaken as the level timer runs.
         *
         * @param progress elapsed share of the level timer (0 to 1)
         */
        static Handling rushHour(double progress) {
            double p = Math.clamp(progress, 0, 1);
            return new Handling(
                    TOP_SPEED + (RUSH_TOP_SPEED - TOP_SPEED) * p, BRAKE - (BRAKE - RUSH_BRAKE) * p);
        }
    }

    /**
     * What the drivers do during one tick.
     *
     * @param gas GAS is pressed
     * @param brake BRAKE is pressed (wins over GAS)
     * @param steer +1 left, -1 right, 0 straight (both sides pressed cancel out)
     */
    record Pedals(boolean gas, boolean brake, int steer) {

        /** Nobody presses anything. */
        static final Pedals IDLE = new Pedals(false, false, 0);
    }

    /** What the bus touched during a step. */
    enum Contact {
        /** Nothing: the bus moved freely. */
        NONE,
        /** Slid along a wall and lost a little speed. */
        SCRAPE,
        /** Hit something solid and stopped. */
        BUMP,
        /** Touched the CEO's car. */
        CEO
    }

    /** The ground as the physics sees it. */
    interface Ground {

        /** The ground of a cell (outside the map: a building). */
        Tile tile(int x, int y);

        /** Whether the bus can't enter the cell right now (solid ground or crossing geese). */
        boolean blocked(int x, int y);
    }

    /**
     * Advances the bus by {@code dt} seconds.
     *
     * @return what the bus touched
     */
    static Contact step(Bus bus, Pedals pedals, Handling handling, double dt, Ground ground) {
        boolean offroad = !ground.tile(bus.cellX(), bus.cellY()).paved();
        double top = offroad ? OFFROAD_TOP_SPEED : handling.topSpeed();
        double speed = nextSpeed(bus.speed(), pedals, handling, top, dt);
        if (pedals.steer() != 0 && speed != 0) {
            double factor = Math.min(1, Math.abs(speed) / FULL_STEER_SPEED);
            double direction = Math.signum(speed);
            bus.turnTo(bus.heading() - pedals.steer() * direction * TURN_RATE * factor * dt);
        }
        double dx = Math.cos(bus.heading()) * speed * dt;
        double dy = Math.sin(bus.heading()) * speed * dt;
        return move(bus, speed, dx, dy, ground);
    }

    private static double nextSpeed(
            double speed, Pedals pedals, Handling handling, double top, double dt) {
        double next = speed;
        if (pedals.brake()) {
            next =
                    next > 0
                            ? Math.max(0, next - handling.brake() * dt)
                            : Math.max(-REVERSE_TOP_SPEED, next - REVERSE_ACCELERATION * dt);
        } else if (pedals.gas()) {
            if (next < top) {
                next = Math.min(top, next + ACCELERATION * dt);
            }
        } else if (next > 0) {
            next = Math.max(0, next - ROLLING_FRICTION * dt);
        } else if (next < 0) {
            next = Math.min(0, next + ROLLING_FRICTION * dt);
        }
        if (next > top) {
            next = Math.max(top, next - OFFROAD_DRAG * dt);
        }
        return next;
    }

    private static Contact move(Bus bus, double speed, double dx, double dy, Ground ground) {
        double x = bus.x();
        double y = bus.y();
        if (touchesCeo(ground, x + dx, y + dy)) {
            return crash(bus, x + dx, y + dy);
        }
        if (free(ground, x + dx, y + dy)) {
            bus.place(x + dx, y + dy);
            bus.setSpeed(speed);
            return Contact.NONE;
        }
        double length = Math.hypot(dx, dy);
        double[][] slides =
                Math.abs(dx) >= Math.abs(dy)
                        ? new double[][] {{dx, 0}, {0, dy}}
                        : new double[][] {{0, dy}, {dx, 0}};
        for (double[] slide : slides) {
            double part = Math.hypot(slide[0], slide[1]);
            if (part < MIN_SLIDE * length) {
                continue;
            }
            double sx = x + slide[0];
            double sy = y + slide[1];
            if (touchesCeo(ground, sx, sy)) {
                return crash(bus, sx, sy);
            }
            if (free(ground, sx, sy)) {
                bus.place(sx, sy);
                bus.setSpeed(speed * SCRAPE_FACTOR);
                return Contact.SCRAPE;
            }
        }
        bus.setSpeed(0);
        return Contact.BUMP;
    }

    private static Contact crash(Bus bus, double x, double y) {
        bus.place(x, y);
        bus.setSpeed(0);
        return Contact.CEO;
    }

    /** Whether a bus centred at the point overlaps no blocked cell. */
    static boolean free(Ground ground, double cx, double cy) {
        for (int ty = (int) Math.floor(cy - Bus.RADIUS);
                ty <= (int) Math.floor(cy + Bus.RADIUS);
                ty++) {
            for (int tx = (int) Math.floor(cx - Bus.RADIUS);
                    tx <= (int) Math.floor(cx + Bus.RADIUS);
                    tx++) {
                if (ground.blocked(tx, ty) && overlaps(cx, cy, tx, ty)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean touchesCeo(Ground ground, double cx, double cy) {
        for (int ty = (int) Math.floor(cy - Bus.RADIUS);
                ty <= (int) Math.floor(cy + Bus.RADIUS);
                ty++) {
            for (int tx = (int) Math.floor(cx - Bus.RADIUS);
                    tx <= (int) Math.floor(cx + Bus.RADIUS);
                    tx++) {
                if (ground.tile(tx, ty) == Tile.CEO_CAR && overlaps(cx, cy, tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Circle (bus) against the unit square of cell {@code (tx, ty)}. */
    private static boolean overlaps(double cx, double cy, int tx, int ty) {
        double nearestX = Math.clamp(cx, tx, tx + 1.0);
        double nearestY = Math.clamp(cy, ty, ty + 1.0);
        double ddx = cx - nearestX;
        double ddy = cy - nearestY;
        return ddx * ddx + ddy * ddy < Bus.RADIUS * Bus.RADIUS;
    }
}
