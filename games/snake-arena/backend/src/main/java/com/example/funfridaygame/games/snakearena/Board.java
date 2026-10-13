package com.example.funfridaygame.games.snakearena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

/**
 * The arena grid with its walls ({@link Bounds}), food and power-ups. Cells are numbered {@code y ·
 * cols + x}; the snakes themselves are kept by the {@link Simulation}. Food and power-ups keep
 * their insertion order, so a seeded game always produces the same frames.
 */
final class Board {

    /** Random picks before falling back to a full scan of the free cells. */
    private static final int RANDOM_PICKS = 64;

    private final int cols;
    private final int rows;
    private final Set<Integer> food = new LinkedHashSet<>();
    private final Map<Integer, PowerUp> powerUps = new LinkedHashMap<>();
    private Bounds bounds;

    /**
     * A power-up waiting to be picked up.
     *
     * @param cell where it lies
     * @param type what it does
     * @param until the tick at which it vanishes
     */
    record PowerUp(int cell, PowerType type, long until) {}

    Board(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.bounds = Bounds.of(cols, rows);
    }

    /** Columns of the grid. */
    int cols() {
        return cols;
    }

    /** Rows of the grid. */
    int rows() {
        return rows;
    }

    /** Number of cells of the grid (walls included). */
    int size() {
        return cols * rows;
    }

    /** The current walls. */
    Bounds bounds() {
        return bounds;
    }

    /** Moves the walls; food and power-ups under the wall vanish. */
    void setBounds(Bounds next) {
        bounds = next;
        food.removeIf(cell -> !inside(cell));
        powerUps.keySet().removeIf(cell -> !inside(cell));
    }

    /** The cell at a position (which must be on the grid). */
    int cell(int x, int y) {
        return y * cols + x;
    }

    /** Column of a cell. */
    int x(int cell) {
        return cell % cols;
    }

    /** Row of a cell. */
    int y(int cell) {
        return cell / cols;
    }

    /** Whether the position is inside the walls. */
    boolean inside(int x, int y) {
        return bounds.contains(x, y);
    }

    /** Whether the cell is inside the walls. */
    boolean inside(int cell) {
        return inside(x(cell), y(cell));
    }

    /** Whether neither food nor a power-up lies on the cell (snakes are the caller's business). */
    boolean isClear(int cell) {
        return !food.contains(cell) && !powerUps.containsKey(cell);
    }

    // ------------------------------------------------------------------ food

    boolean hasFood(int cell) {
        return food.contains(cell);
    }

    void addFood(int cell) {
        food.add(cell);
    }

    void removeFood(int cell) {
        food.remove(cell);
    }

    /** Moves a piece of food (magnets). */
    void moveFood(int from, int to) {
        if (food.remove(from)) {
            food.add(to);
        }
    }

    int foodCount() {
        return food.size();
    }

    /** Food cells in a stable order. */
    List<Integer> food() {
        return List.copyOf(food);
    }

    /** Test hook: takes all food off the board. */
    void clearFood() {
        food.clear();
    }

    // ------------------------------------------------------------------ power-ups

    /** The power-up on a cell, or {@code null}. */
    PowerUp powerUpAt(int cell) {
        return powerUps.get(cell);
    }

    void addPowerUp(PowerUp powerUp) {
        powerUps.put(powerUp.cell(), powerUp);
    }

    /** Removes and returns the power-up on a cell ({@code null} if there is none). */
    PowerUp takePowerUp(int cell) {
        return powerUps.remove(cell);
    }

    Collection<PowerUp> powerUps() {
        return Collections.unmodifiableCollection(powerUps.values());
    }

    /** Removes the power-ups whose time is up. */
    void expirePowerUps(long tick) {
        powerUps.values().removeIf(powerUp -> powerUp.until() <= tick);
    }

    // ------------------------------------------------------------------ free cells

    /**
     * A random clear cell inside the walls, at least {@code margin} cells away from them, that
     * {@code allowed} accepts (e.g. not on a snake); {@code -1} when there is none.
     */
    int randomFreeCell(RandomGenerator random, int margin, IntPredicate allowed) {
        int x0 = bounds.x0() + margin;
        int x1 = bounds.x1() - margin;
        int y0 = bounds.y0() + margin;
        int y1 = bounds.y1() - margin;
        if (x0 > x1 || y0 > y1) {
            return -1;
        }
        for (int i = 0; i < RANDOM_PICKS; i++) {
            int cell = cell(x0 + random.nextInt(x1 - x0 + 1), y0 + random.nextInt(y1 - y0 + 1));
            if (isClear(cell) && allowed.test(cell)) {
                return cell;
            }
        }
        List<Integer> free = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int cell = cell(x, y);
                if (isClear(cell) && allowed.test(cell)) {
                    free.add(cell);
                }
            }
        }
        return free.isEmpty() ? -1 : free.get(random.nextInt(free.size()));
    }
}
