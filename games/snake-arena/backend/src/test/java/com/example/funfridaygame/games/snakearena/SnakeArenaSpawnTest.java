package com.example.funfridaygame.games.snakearena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for where snakes appear: the start slots of a round for every arena size and
 * player count, and respawns after a crash — on a free spot away from the walls and from other
 * snakes' heads, on food when nothing else is left, or a little later when there is no room at all.
 */
class SnakeArenaSpawnTest {

    private static final String[] PLAYERS = {
        "p01", "p02", "p03", "p04", "p05", "p06", "p07", "p08", "p09", "p10", "p11", "p12"
    };

    /** Preferred headings of a respawn when several sides are equally free. */
    private static final List<Dir> HEADINGS = List.of(Dir.RIGHT, Dir.LEFT, Dir.DOWN, Dir.UP);

    // ------------------------------------------------------------------ start slots

    @Test
    void everyArenaSizeHasSafeDistinctStartSlotsForOneToTwelvePlayers() {
        for (String size : List.of("small", "medium", "large")) {
            for (int count = 1; count <= PLAYERS.length; count++) {
                TestArena arena =
                        TestArena.start(Map.of("arena", size), Arrays.copyOf(PLAYERS, count));
                Map<String, Object> view = arena.game.spectatorView();
                Grid grid = new Grid((Integer) view.get("cols"), (Integer) view.get("rows"));
                List<Map<String, Object>> snakes = snakes(arena.frame());
                String where = size + " arena with " + count + " players";

                assertThat(snakes).as(where).hasSize(count);
                Set<Integer> taken = new HashSet<>();
                for (Map<String, Object> snake : snakes) {
                    List<Integer> body = body(snake);
                    Dir dir = Dir.valueOf((String) snake.get("dir"));
                    assertThat(body).as(where).hasSize(Spawner.START_LENGTH);
                    for (int i = 0; i < body.size(); i++) {
                        int cell = body.get(i);
                        assertThat(grid.x(cell))
                                .as(where + ": away from the side walls")
                                .isBetween(Spawner.WALL_GAP, grid.cols - 1 - Spawner.WALL_GAP);
                        assertThat(grid.y(cell))
                                .as(where + ": away from the top and bottom walls")
                                .isBetween(Spawner.WALL_GAP, grid.rows - 1 - Spawner.WALL_GAP);
                        assertThat(taken.add(cell)).as(where + ": snakes overlap").isTrue();
                        if (i > 0) {
                            assertThat(cell)
                                    .as(where + ": a straight body behind the head")
                                    .isEqualTo(body.get(i - 1) - grid.step(dir));
                        }
                    }
                    assertThat(grid.headsInward(body))
                            .as(where + ": heads for the middle")
                            .isTrue();
                }
                assertThat(headOnPairs(grid, snakes)).as(where + ": head-on on one line").isZero();
            }
        }
    }

    @Test
    void slotsFillTheWallsInTurnAndInterleaveOnOppositeWalls() {
        TestArena arena = TestArena.start(Map.of("arena", "small"), "a", "b", "c", "d", "e");

        assertThat(heading(arena, "a")).isEqualTo("RIGHT at 6,6");
        assertThat(heading(arena, "b")).isEqualTo("LEFT at 29,12");
        assertThat(heading(arena, "c")).isEqualTo("DOWN at 14,6");
        assertThat(heading(arena, "d")).isEqualTo("UP at 21,17");
        assertThat(heading(arena, "e")).as("back to the left wall").isEqualTo("RIGHT at 6,17");
    }

    // ------------------------------------------------------------------ respawns

    @Test
    void respawnsLandOnAFreeSpotInsideTheWallsThatNoHeadPointsAt() {
        for (long seed = 1; seed <= 25; seed++) {
            TestArena arena = TestArena.start(Map.of(), seed, "alice", "bob", "carol").calm().go();
            arena.place("bob", Dir.RIGHT, 14, 4, 13, 4, 12, 4, 11, 4)
                    .circle("bob", 10, 4, 24, 9)
                    .place("carol", Dir.RIGHT, 14, 14, 13, 14, 12, 14, 11, 14)
                    .circle("carol", 10, 14, 24, 19)
                    .place("alice", Dir.RIGHT, 35, 22, 34, 22, 33, 22, 32, 22)
                    .ticks(1);
            int respawnAt = respawnAt(arena, "alice");
            assertThat(respawnAt).isEqualTo(arena.sim().tick() + Simulation.RESPAWN_TICKS);
            arena.until(respawnAt - 1);
            Map<String, Object> before = arena.frame();

            arena.ticks(1);

            String where = "seed " + seed;
            Map<String, Object> alice = arena.frameSnake("alice");
            assertThat(alice).as(where).containsEntry("alive", true).containsEntry("length", 4);
            Grid grid = new Grid(36, 24);
            List<Integer> body = body(alice);
            Dir dir = Dir.valueOf((String) alice.get("dir"));
            int center = body.get(1);
            assertThat(body.getFirst()).as(where).isEqualTo(center + grid.step(dir));
            assertThat(body.get(2)).as(where).isEqualTo(center - grid.step(dir));
            assertThat(body.get(3)).as(where).isEqualTo(center - 2 * grid.step(dir));

            Set<Integer> occupied = occupied(before);
            Set<Integer> danger = grid.inFrontOfHeads(snakes(before), Spawner.HEAD_RAY);
            List<Integer> food = food(before);
            for (int cell : grid.area(center, Spawner.AREA_RADIUS)) {
                assertThat(grid.x(cell)).as(where + ": a cell from the wall").isBetween(1, 34);
                assertThat(grid.y(cell)).as(where + ": a cell from the wall").isBetween(1, 22);
                assertThat(occupied).as(where + ": on a snake").doesNotContain(cell);
                assertThat(danger).as(where + ": in front of a head").doesNotContain(cell);
                assertThat(food).as(where + ": on food").doesNotContain(cell);
            }
            assertThat(dir)
                    .as(where + ": heads for the freest side")
                    .isEqualTo(grid.freestHeading(center, occupied));
        }
    }

    @Test
    void onAnArenaFullOfFoodARespawnClearsTheFoodUnderItWithoutScoring() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("bob", Dir.RIGHT, 14, 4, 13, 4, 12, 4, 11, 4)
                .circle("bob", 10, 4, 24, 9)
                .place("alice", Dir.RIGHT, 35, 22, 34, 22, 33, 22, 32, 22)
                .ticks(1);
        int respawnAt = respawnAt(arena, "alice");
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 36; x++) {
                boolean onBobsLoop =
                        (x >= 10 && x <= 24 && (y == 4 || y == 9))
                                || (y >= 4 && y <= 9 && (x == 10 || x == 24));
                if (!onBobsLoop) {
                    arena.food(x, y);
                }
            }
        }

        arena.until(respawnAt);

        Map<String, Object> alice = arena.frameSnake("alice");
        assertThat(alice)
                .containsEntry("alive", true)
                .containsEntry("length", Spawner.START_LENGTH)
                .containsEntry("points", 0);
        assertThat(food(arena.frame()))
                .hasSizeGreaterThan(800)
                .doesNotContainAnyElementsOf(body(alice));
    }

    @Test
    void aCrashedSnakeWaitsWhileThereIsNoRoomAndComesBackAsSoonAsThereIs() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 35, 12, 34, 12, 33, 12, 32, 12).ticks(1);
        int respawnAt = respawnAt(arena, "alice");
        // Bob winds through every third row: no empty 5×5 area is left anywhere.
        arena.calm().place("bob", Dir.RIGHT, serpentine());

        arena.until(respawnAt + 1);

        assertThat(arena.frameSnake("alice"))
                .as("no room: still waiting")
                .containsEntry("alive", false)
                .containsEntry("respawnAt", respawnAt);
        assertThat(arena.frameSnake("bob")).containsEntry("alive", true);

        arena.game.leave("bob");
        arena.ticks(1);

        assertThat(arena.frameSnake("alice")).containsEntry("alive", true);
        assertThat(arena.frameSnake("alice").get("respawnAt")).isNull();
    }

    // ------------------------------------------------------------------ helpers

    /** The grid geometry of an arena. */
    private record Grid(int cols, int rows) {

        int x(int cell) {
            return cell % cols;
        }

        int y(int cell) {
            return cell / cols;
        }

        boolean inside(int x, int y) {
            return x >= 0 && y >= 0 && x < cols && y < rows;
        }

        /** The cell offset of one step. */
        int step(Dir dir) {
            return dir.dx() + dir.dy() * cols;
        }

        /** The head is nearer to the middle than the tail. */
        boolean headsInward(List<Integer> body) {
            double middleX = (cols - 1) / 2.0;
            double middleY = (rows - 1) / 2.0;
            int head = body.getFirst();
            int tail = body.getLast();
            return Math.hypot(x(head) - middleX, y(head) - middleY)
                    < Math.hypot(x(tail) - middleX, y(tail) - middleY);
        }

        /** The square of cells within {@code radius} of {@code center}. */
        List<Integer> area(int center, int radius) {
            List<Integer> cells = new ArrayList<>();
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    cells.add(center + dx + dy * cols);
                }
            }
            return cells;
        }

        /** The {@code ray} cells in front of every living snake's head. */
        Set<Integer> inFrontOfHeads(List<Map<String, Object>> snakes, int ray) {
            Set<Integer> cells = new HashSet<>();
            for (Map<String, Object> snake : snakes) {
                if (!Boolean.TRUE.equals(snake.get("alive"))) {
                    continue;
                }
                Dir dir = Dir.valueOf((String) snake.get("dir"));
                int head = body(snake).getFirst();
                for (int k = 1; k <= ray; k++) {
                    int ax = x(head) + k * dir.dx();
                    int ay = y(head) + k * dir.dy();
                    if (inside(ax, ay)) {
                        cells.add(ax + ay * cols);
                    }
                }
            }
            return cells;
        }

        /**
         * The heading with the longest free run from {@code from} (ties: right, left, down, up).
         */
        Dir freestHeading(int from, Set<Integer> occupied) {
            Dir best = HEADINGS.getFirst();
            int bestRun = -1;
            for (Dir dir : HEADINGS) {
                int run = 0;
                int x = x(from) + dir.dx();
                int y = y(from) + dir.dy();
                while (inside(x, y) && !occupied.contains(x + y * cols)) {
                    run++;
                    x += dir.dx();
                    y += dir.dy();
                }
                if (run > bestRun) {
                    best = dir;
                    bestRun = run;
                }
            }
            return best;
        }
    }

    /** "RIGHT at 6,7": a snake's heading and head, as the view shows them. */
    private static String heading(TestArena arena, String id) {
        int[] head = arena.head(id);
        return arena.frameSnake(id).get("dir") + " at " + head[0] + "," + head[1];
    }

    /** Pairs of snakes on the same row (column) heading towards each other. */
    private static int headOnPairs(Grid grid, List<Map<String, Object>> snakes) {
        int pairs = 0;
        for (Map<String, Object> a : snakes) {
            for (Map<String, Object> b : snakes) {
                int ha = body(a).getFirst();
                int hb = body(b).getFirst();
                boolean rows =
                        "RIGHT".equals(a.get("dir"))
                                && "LEFT".equals(b.get("dir"))
                                && grid.y(ha) == grid.y(hb)
                                && grid.x(ha) < grid.x(hb);
                boolean cols =
                        "DOWN".equals(a.get("dir"))
                                && "UP".equals(b.get("dir"))
                                && grid.x(ha) == grid.x(hb)
                                && grid.y(ha) < grid.y(hb);
                if (rows || cols) {
                    pairs++;
                }
            }
        }
        return pairs;
    }

    /**
     * A snake winding through rows 1, 4, 7, … 22 of the small arena, joined at alternating ends,
     * whose head has just turned onto the free bottom row heading right (x, y pairs, head first).
     */
    private static int[] serpentine() {
        List<int[]> path = new ArrayList<>(); // tail first
        int[] rows = {1, 4, 7, 10, 13, 16, 19, 22};
        for (int r = 0; r < rows.length; r++) {
            boolean rightwards = r % 2 == 0;
            for (int i = 0; i < 34; i++) {
                path.add(new int[] {rightwards ? 1 + i : 34 - i, rows[r]});
            }
            if (r + 1 < rows.length) {
                int x = rightwards ? 34 : 1;
                path.add(new int[] {x, rows[r] + 1});
                path.add(new int[] {x, rows[r] + 2});
            }
        }
        path.add(new int[] {0, 22});
        path.add(new int[] {0, 23});
        path.add(new int[] {1, 23});
        Collections.reverse(path);
        return path.stream().flatMapToInt(Arrays::stream).toArray();
    }

    private static int respawnAt(TestArena arena, String id) {
        Map<String, Object> snake = arena.frameSnake(id);
        assertThat(snake).containsEntry("alive", false);
        return (Integer) snake.get("respawnAt");
    }

    /** Every cell of a living snake in a frame. */
    private static Set<Integer> occupied(Map<String, Object> frame) {
        Set<Integer> cells = new HashSet<>();
        for (Map<String, Object> snake : snakes(frame)) {
            cells.addAll(body(snake));
        }
        return cells;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> snakes(Map<String, Object> frame) {
        return (List<Map<String, Object>>) frame.get("snakes");
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> body(Map<String, Object> snake) {
        return (List<Integer>) snake.get("body");
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> food(Map<String, Object> frame) {
        return (List<Integer>) frame.get("food");
    }
}
