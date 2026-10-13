package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.games.snakearena.Board.PowerUp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves a group of snakes one cell at the same time and decides who crashes.
 *
 * <p>All movers take their next heading and compute their new head together. Tails move on unless
 * the snake eats this step, so entering the cell a tail is leaving is safe. A mover crashes when
 * its new head is
 *
 * <ol>
 *   <li>outside the walls ({@link Cause#WALL}),
 *   <li>on its own body ({@link Cause#SELF}),
 *   <li>on the head of a snake that moves onto its head at the same time — a head swap ({@link
 *       Cause#HEAD}, both crash),
 *   <li>on another snake's body ({@link Cause#BODY}, credited to that snake), or
 *   <li>on another snake's new head ({@link Cause#HEAD}, both crash).
 * </ol>
 *
 * <p>Ghosts ({@link PowerType#GHOST}) are intangible for other snakes in both directions: a ghost
 * passes through other bodies and other snakes pass through a ghost's body. Walls, its own body and
 * head collisions still count. Snakes that don't move in this step are obstacles with their whole
 * body, head included.
 */
final class MoveResolver {

    /** Why a snake crashed. */
    enum Cause {
        WALL,
        SELF,
        BODY,
        HEAD
    }

    /**
     * A crash.
     *
     * @param snake who crashed
     * @param cause why
     * @param other the body's owner ({@link Cause#BODY}) or the other head ({@link Cause#HEAD})
     */
    record Crash(Snake snake, Cause cause, Snake other) {}

    private MoveResolver() {}

    /**
     * Moves {@code movers} (some of the {@code alive} snakes) one cell. Survivors advance, eat food
     * and pick up power-ups; crashed snakes are returned untouched for the caller to remove.
     */
    static List<Crash> step(Board board, List<Snake> alive, List<Snake> movers, long tick) {
        int count = movers.size();
        int[] heads = new int[count];
        boolean[] grows = new boolean[count];
        Map<Snake, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < count; i++) {
            Snake snake = movers.get(i);
            index.put(snake, i);
            Dir dir = snake.nextMove(tick);
            int x = board.x(snake.head()) + dir.dx();
            int y = board.y(snake.head()) + dir.dy();
            heads[i] = board.inside(x, y) ? board.cell(x, y) : -1;
            grows[i] = heads[i] >= 0 && board.hasFood(heads[i]);
        }
        Map<Integer, List<Snake>> solid = solidCells(alive, index, grows);

        List<Crash> crashes = new ArrayList<>();
        boolean[] crashed = new boolean[count];
        for (int i = 0; i < count; i++) {
            Crash crash = judge(movers, heads, solid, i);
            if (crash != null) {
                crashes.add(crash);
                crashed[i] = true;
            }
        }
        for (int i = 0; i < count; i++) {
            if (!crashed[i]) {
                move(board, movers.get(i), heads[i], grows[i], tick);
            }
        }
        return crashes;
    }

    /** Every body cell after this step's tails have moved on, with its owners. */
    private static Map<Integer, List<Snake>> solidCells(
            List<Snake> alive, Map<Snake, Integer> index, boolean[] grows) {
        Map<Integer, List<Snake>> solid = new HashMap<>();
        for (Snake snake : alive) {
            Integer mover = index.get(snake);
            int keep = mover != null && !grows[mover] ? snake.length() - 1 : snake.length();
            int seen = 0;
            for (int cell : snake.body()) {
                if (seen++ >= keep) {
                    break;
                }
                solid.computeIfAbsent(cell, key -> new ArrayList<>(1)).add(snake);
            }
        }
        return solid;
    }

    private static Crash judge(
            List<Snake> movers, int[] heads, Map<Integer, List<Snake>> solid, int i) {
        Snake snake = movers.get(i);
        int head = heads[i];
        if (head < 0) {
            return new Crash(snake, Cause.WALL, null);
        }
        List<Snake> owners = solid.getOrDefault(head, List.of());
        if (owners.contains(snake)) {
            return new Crash(snake, Cause.SELF, null);
        }
        for (int j = 0; j < movers.size(); j++) {
            if (j != i && heads[j] == snake.head() && head == movers.get(j).head()) {
                return new Crash(snake, Cause.HEAD, movers.get(j));
            }
        }
        if (!snake.has(PowerType.GHOST)) {
            for (Snake owner : owners) {
                if (!owner.has(PowerType.GHOST)) {
                    return new Crash(snake, Cause.BODY, owner);
                }
            }
        }
        for (int j = 0; j < movers.size(); j++) {
            if (j != i && heads[j] == head) {
                return new Crash(snake, Cause.HEAD, movers.get(j));
            }
        }
        return null;
    }

    private static void move(Board board, Snake snake, int head, boolean grow, long tick) {
        snake.advance(head, grow);
        if (grow) {
            board.removeFood(head);
            snake.ate();
        }
        PowerUp powerUp = board.takePowerUp(head);
        if (powerUp != null) {
            snake.grant(powerUp.type(), tick + powerUp.type().ticks());
        }
    }
}
