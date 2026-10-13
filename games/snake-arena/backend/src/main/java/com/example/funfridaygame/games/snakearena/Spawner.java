package com.example.funfridaygame.games.snakearena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Where snakes appear: fixed start slots for a round and safe random spots for respawns.
 *
 * <p><b>Start slots</b> sit along the four walls, {@value #WALL_GAP} cells away from them, and head
 * for the open middle. They are filled left, right, top, bottom, left, … and spread evenly along
 * each wall; snakes on opposite walls get interleaved rows (columns), so no two snakes ever start
 * head-on on the same line, and the top/bottom snakes keep clear of the corners where the left and
 * right snakes lie.
 *
 * <p><b>Respawns</b> pick a random 5×5 area that is empty, inside the walls and not within {@value
 * #HEAD_RAY} cells in front of another snake's head; the snake (length {@value #START_LENGTH}) lies
 * across its middle and heads for the side with the most free cells.
 */
final class Spawner {

    /** Length of a new snake. */
    static final int START_LENGTH = 4;

    /** Free cells between the walls and a snake at the start of a round. */
    static final int WALL_GAP = 3;

    /** Columns at each side kept free of top/bottom snakes (the left/right snakes lie there). */
    static final int CORNER_CLEARANCE = 10;

    /** Half the side of the empty area a respawn needs (5×5). */
    static final int AREA_RADIUS = 2;

    /** Cells in front of another head that a respawn area must not touch. */
    static final int HEAD_RAY = 4;

    private static final int ATTEMPTS = 200;

    /** Preferred headings when several sides are equally free. */
    private static final Dir[] HEADINGS = {Dir.RIGHT, Dir.LEFT, Dir.DOWN, Dir.UP};

    /**
     * Where and how a snake appears.
     *
     * @param cells its body, head first
     * @param dir its heading
     */
    record Placement(List<Integer> cells, Dir dir) {}

    private Spawner() {}

    // ------------------------------------------------------------------ start slots

    /** The start slots for {@code count} snakes on the board's full grid, in fill order. */
    static List<Placement> startSlots(Board board, int count) {
        int cols = board.cols();
        int rows = board.rows();
        int[] perSide = new int[4];
        for (int i = 0; i < count; i++) {
            perSide[i % 4]++;
        }
        int[] sideRows = spread(perSide[0] + perSide[1], WALL_GAP, rows - 1 - WALL_GAP);
        int[] sideCols =
                spread(perSide[2] + perSide[3], CORNER_CLEARANCE, cols - 1 - CORNER_CLEARANCE);
        int near = WALL_GAP + START_LENGTH - 1;
        int[] used = new int[4];
        List<Placement> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int side = i % 4;
            int k = used[side]++;
            slots.add(
                    switch (side) {
                        case 0 -> line(board, near, sideRows[2 * k], Dir.RIGHT);
                        case 1 -> line(board, cols - 1 - near, sideRows[2 * k + 1], Dir.LEFT);
                        case 2 -> line(board, sideCols[2 * k], near, Dir.DOWN);
                        default -> line(board, sideCols[2 * k + 1], rows - 1 - near, Dir.UP);
                    });
        }
        return slots;
    }

    /** {@code count} evenly spread positions between {@code lo} and {@code hi}. */
    private static int[] spread(int count, int lo, int hi) {
        int[] positions = new int[count];
        for (int j = 0; j < count; j++) {
            positions[j] = lo + (int) Math.round((hi - lo) * (2.0 * j + 1) / (2.0 * count));
        }
        return positions;
    }

    /** A straight snake whose head is at (x, y), heading {@code dir}. */
    private static Placement line(Board board, int x, int y, Dir dir) {
        List<Integer> cells = new ArrayList<>(START_LENGTH);
        for (int i = 0; i < START_LENGTH; i++) {
            cells.add(board.cell(x - i * dir.dx(), y - i * dir.dy()));
        }
        return new Placement(List.copyOf(cells), dir);
    }

    // ------------------------------------------------------------------ respawns

    /**
     * A safe spot for a respawn: preferably an area without food and power-ups, otherwise one that
     * is merely free of snakes (the food under the new body is then eaten up by the caller).
     *
     * @param occupied cells taken by snakes
     * @param others the snakes on the board, whose heads must not point at the area
     */
    static Optional<Placement> respawn(
            Board board, boolean[] occupied, Collection<Snake> others, RandomGenerator random) {
        Set<Integer> danger = dangerZone(board, others);
        Optional<Placement> clear = search(board, occupied, danger, random, true);
        return clear.isPresent() ? clear : search(board, occupied, danger, random, false);
    }

    /** The cells right in front of every head. */
    private static Set<Integer> dangerZone(Board board, Collection<Snake> others) {
        Set<Integer> danger = new HashSet<>();
        for (Snake snake : others) {
            if (!snake.alive()) {
                continue;
            }
            int x = board.x(snake.head());
            int y = board.y(snake.head());
            for (int k = 1; k <= HEAD_RAY; k++) {
                int ax = x + k * snake.dir().dx();
                int ay = y + k * snake.dir().dy();
                if (ax >= 0 && ay >= 0 && ax < board.cols() && ay < board.rows()) {
                    danger.add(board.cell(ax, ay));
                }
            }
        }
        return danger;
    }

    private static Optional<Placement> search(
            Board board,
            boolean[] occupied,
            Set<Integer> danger,
            RandomGenerator random,
            boolean clearOnly) {
        Bounds bounds = board.bounds();
        int margin = AREA_RADIUS + 1;
        int x0 = bounds.x0() + margin;
        int x1 = bounds.x1() - margin;
        int y0 = bounds.y0() + margin;
        int y1 = bounds.y1() - margin;
        if (x0 > x1 || y0 > y1) {
            return Optional.empty();
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int cx = x0 + random.nextInt(x1 - x0 + 1);
            int cy = y0 + random.nextInt(y1 - y0 + 1);
            if (areaFree(board, occupied, danger, cx, cy, clearOnly)) {
                Dir dir = freestHeading(board, occupied, cx, cy);
                List<Integer> cells = new ArrayList<>(START_LENGTH);
                for (int i = -1; i < START_LENGTH - 1; i++) {
                    cells.add(board.cell(cx - i * dir.dx(), cy - i * dir.dy()));
                }
                return Optional.of(new Placement(List.copyOf(cells), dir));
            }
        }
        return Optional.empty();
    }

    private static boolean areaFree(
            Board board,
            boolean[] occupied,
            Set<Integer> danger,
            int cx,
            int cy,
            boolean clearOnly) {
        for (int dy = -AREA_RADIUS; dy <= AREA_RADIUS; dy++) {
            for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (!board.inside(x, y)) {
                    return false;
                }
                int cell = board.cell(x, y);
                if (occupied[cell]
                        || danger.contains(cell)
                        || (clearOnly && !board.isClear(cell))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The heading with the longest free run from (x, y). */
    private static Dir freestHeading(Board board, boolean[] occupied, int x, int y) {
        Dir best = HEADINGS[0];
        int bestRun = -1;
        for (Dir dir : HEADINGS) {
            int run = 0;
            int ax = x + dir.dx();
            int ay = y + dir.dy();
            while (board.inside(ax, ay) && !occupied[board.cell(ax, ay)]) {
                run++;
                ax += dir.dx();
                ay += dir.dy();
            }
            if (run > bestRun) {
                best = dir;
                bestRun = run;
            }
        }
        return best;
    }
}
