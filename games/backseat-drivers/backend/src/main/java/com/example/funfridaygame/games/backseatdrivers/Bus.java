package com.example.funfridaygame.games.backseatdrivers;

/**
 * The shuttle: position and heading in tile units, speed in tiles per second (negative while
 * reversing). Mutable; owned by one {@link Simulation} and moved by {@link BusPhysics}.
 */
final class Bus {

    /** The bus collides as a circle of this radius (tiles). */
    static final double RADIUS = 0.45;

    private double x;
    private double y;
    private double heading;
    private double speed;

    /**
     * A bus standing still.
     *
     * @param x centre, x
     * @param y centre, y
     * @param heading radians, 0 = east, π/2 = south
     */
    Bus(double x, double y, double heading) {
        this.x = x;
        this.y = y;
        this.heading = heading;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double heading() {
        return heading;
    }

    double speed() {
        return speed;
    }

    /** Moves the bus. */
    void place(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }

    /** Turns the bus; the angle is normalised to (-π, π]. */
    void turnTo(double radians) {
        double angle = radians % (2 * Math.PI);
        if (angle <= -Math.PI) {
            angle += 2 * Math.PI;
        } else if (angle > Math.PI) {
            angle -= 2 * Math.PI;
        }
        this.heading = angle;
    }

    void setSpeed(double value) {
        this.speed = value;
    }

    /** Column of the cell under the bus centre. */
    int cellX() {
        return (int) Math.floor(x);
    }

    /** Row of the cell under the bus centre. */
    int cellY() {
        return (int) Math.floor(y);
    }
}
