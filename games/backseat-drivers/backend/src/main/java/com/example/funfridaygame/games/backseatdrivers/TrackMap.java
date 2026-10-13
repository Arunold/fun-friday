package com.example.funfridaygame.games.backseatdrivers;

import java.util.ArrayList;
import java.util.List;

/**
 * One level's map: the ground of every cell plus the start and the hazards placed on it. Immutable;
 * built and validated by {@link TrackLoader}.
 *
 * <p>Coordinates are tile units: cell {@code (x, y)} covers {@code [x, x+1) × [y, y+1)}, x grows to
 * the east and y to the south. Everything outside the map counts as a building.
 */
final class TrackMap {

    /** A map cell. */
    record Cell(int x, int y) {

        /** The cell's centre, x. */
        double centerX() {
            return x + 0.5;
        }

        /** The cell's centre, y. */
        double centerY() {
            return y + 0.5;
        }

        /** {@code [x, y]} for the views. */
        int[] toArray() {
            return new int[] {x, y};
        }
    }

    /**
     * A flock of geese: the road cells it blocks while crossing.
     *
     * @param index position in {@link #geese()} (also the bit in the frames' crossing mask)
     * @param cells connected crossing cells
     */
    record Flock(int index, List<Cell> cells) {

        Flock {
            cells = List.copyOf(cells);
        }

        /** Distance from a point to the nearest cell centre of the flock. */
        double distanceTo(double x, double y) {
            double best = Double.MAX_VALUE;
            for (Cell cell : cells) {
                best = Math.min(best, Math.hypot(cell.centerX() - x, cell.centerY() - y));
            }
            return best;
        }
    }

    /** Compass direction the bus faces at the start. */
    enum Heading {
        N(-Math.PI / 2),
        E(0),
        S(Math.PI / 2),
        W(Math.PI);

        private final double radians;

        Heading(double radians) {
            this.radians = radians;
        }

        /** Heading in radians: 0 = east, π/2 = south (y grows downwards). */
        double radians() {
            return radians;
        }

        /** Unit step (-1, 0 or 1) of this direction along x. */
        int dx() {
            return (int) Math.round(Math.cos(radians));
        }

        /** Unit step (-1, 0 or 1) of this direction along y. */
        int dy() {
            return (int) Math.round(Math.sin(radians));
        }
    }

    private final String name;
    private final int width;
    private final int height;
    private final Tile[] tiles;
    private final Cell start;
    private final Heading heading;
    private final List<Cell> cones;
    private final List<Cell> coffees;
    private final List<Flock> geese;
    private final List<Cell> ceoCars;
    private final List<Cell> parking;

    /**
     * A map; use {@link TrackLoader#parse} to build one from text.
     *
     * @param name display name, e.g. "Campus Loop"
     * @param width columns
     * @param height rows
     * @param tiles ground, row by row ({@code width × height})
     * @param start the bus's starting cell
     * @param heading where the bus faces at the start
     * @param cones cone cells
     * @param coffees coffee cells
     * @param geese goose crossings
     */
    TrackMap(
            String name,
            int width,
            int height,
            Tile[] tiles,
            Cell start,
            Heading heading,
            List<Cell> cones,
            List<Cell> coffees,
            List<Flock> geese) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = tiles.clone();
        this.start = start;
        this.heading = heading;
        this.cones = List.copyOf(cones);
        this.coffees = List.copyOf(coffees);
        this.geese = List.copyOf(geese);
        this.ceoCars = cellsOf(Tile.CEO_CAR);
        this.parking = cellsOf(Tile.PARKING);
    }

    String name() {
        return name;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    Cell start() {
        return start;
    }

    Heading heading() {
        return heading;
    }

    List<Cell> cones() {
        return cones;
    }

    List<Cell> coffees() {
        return coffees;
    }

    List<Flock> geese() {
        return geese;
    }

    List<Cell> ceoCars() {
        return ceoCars;
    }

    List<Cell> parking() {
        return parking;
    }

    /** Whether the cell lies on the map. */
    boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** The ground of a cell; outside the map everything is a building. */
    Tile tile(int x, int y) {
        return inside(x, y) ? tiles[y * width + x] : Tile.BUILDING;
    }

    /** The ground under a point. */
    Tile tileAt(double x, double y) {
        return tile((int) Math.floor(x), (int) Math.floor(y));
    }

    /** One row of ground characters (see {@link Tile#symbol()}). */
    String row(int y) {
        StringBuilder row = new StringBuilder(width);
        for (int x = 0; x < width; x++) {
            row.append(tile(x, y).symbol());
        }
        return row.toString();
    }

    private List<Cell> cellsOf(Tile kind) {
        ArrayList<Cell> cells = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tile(x, y) == kind) {
                    cells.add(new Cell(x, y));
                }
            }
        }
        return List.copyOf(cells);
    }
}
