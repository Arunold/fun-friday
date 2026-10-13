package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.AreaView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.GooseView;
import com.example.funfridaygame.games.backseatdrivers.Simulation.GooseState;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Cell;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Flock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Cuts views out of the world: the whole map for the navigator (and for everyone once a level is
 * over) or the drivers' window around the bus.
 *
 * <p>The window contains only cells whose centre lies within {@value #VIEW_RADIUS} tiles of the
 * centre of the bus's cell; everything else is {@code ?}, so no map data beyond the area around the
 * bus ever leaves the server for drivers and spectators. Browsers fog the view from about 4.6 tiles
 * and are opaque at 6.1, leaving a margin for the bus moving inside its cell and for interpolation.
 */
final class Areas {

    /** Cells up to this far from the bus's cell are in the drivers' window. */
    static final double VIEW_RADIUS = 7.5;

    /** Character for cells outside the window's circle. */
    static final char UNKNOWN = '?';

    private Areas() {}

    /**
     * The whole map with the hazards that are still around.
     *
     * @param level level number
     * @param sim the level's world
     */
    static AreaView fullMap(int level, Simulation sim) {
        TrackMap map = sim.map();
        List<String> rows = new ArrayList<>(map.height());
        for (int y = 0; y < map.height(); y++) {
            rows.add(map.row(y));
        }
        return area(level, 0, 0, 0, map.width(), map.height(), rows, sim, (x, y) -> true);
    }

    /**
     * The drivers' window around the bus.
     *
     * @param level level number
     * @param version the window's version
     * @param sim the level's world
     */
    static AreaView window(int level, int version, Simulation sim) {
        TrackMap map = sim.map();
        int bx = Math.clamp(sim.bus().cellX(), 0, map.width() - 1);
        int by = Math.clamp(sim.bus().cellY(), 0, map.height() - 1);
        int reach = (int) Math.floor(VIEW_RADIUS);
        int x0 = Math.max(0, bx - reach);
        int y0 = Math.max(0, by - reach);
        int x1 = Math.min(map.width() - 1, bx + reach);
        int y1 = Math.min(map.height() - 1, by + reach);
        BiPredicate<Integer, Integer> visible =
                (x, y) -> {
                    int dx = x - bx;
                    int dy = y - by;
                    return dx * dx + dy * dy <= VIEW_RADIUS * VIEW_RADIUS;
                };
        List<String> rows = new ArrayList<>(y1 - y0 + 1);
        for (int y = y0; y <= y1; y++) {
            StringBuilder row = new StringBuilder(x1 - x0 + 1);
            for (int x = x0; x <= x1; x++) {
                row.append(visible.test(x, y) ? map.tile(x, y).symbol() : UNKNOWN);
            }
            rows.add(row.toString());
        }
        return area(level, version, x0, y0, x1 - x0 + 1, y1 - y0 + 1, rows, sim, visible);
    }

    private static AreaView area(
            int level,
            int version,
            int x0,
            int y0,
            int width,
            int height,
            List<String> rows,
            Simulation sim,
            BiPredicate<Integer, Integer> visible) {
        TrackMap map = sim.map();
        List<int[]> cones = new ArrayList<>();
        for (int i = 0; i < map.cones().size(); i++) {
            Cell cell = map.cones().get(i);
            if (sim.coneLeft(i) && visible.test(cell.x(), cell.y())) {
                cones.add(cell.toArray());
            }
        }
        List<int[]> coffees = new ArrayList<>();
        for (int i = 0; i < map.coffees().size(); i++) {
            Cell cell = map.coffees().get(i);
            if (sim.coffeeLeft(i) && visible.test(cell.x(), cell.y())) {
                coffees.add(cell.toArray());
            }
        }
        List<GooseView> geese = new ArrayList<>();
        for (Flock flock : map.geese()) {
            if (sim.goose(flock.index()) == GooseState.GONE) {
                continue;
            }
            List<int[]> cells =
                    flock.cells().stream()
                            .filter(cell -> visible.test(cell.x(), cell.y()))
                            .map(Cell::toArray)
                            .toList();
            if (!cells.isEmpty()) {
                geese.add(new GooseView(flock.index(), cells));
            }
        }
        return new AreaView(level, version, x0, y0, width, height, rows, cones, coffees, geese);
    }
}
