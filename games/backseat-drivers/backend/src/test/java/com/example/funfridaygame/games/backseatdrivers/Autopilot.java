package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.holders;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.input;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.level;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Plays a level the way a decent team would, through the engine: the "navigator" reads the map from
 * the navigator's view and plans a path along the middle of the roads to the parking lot; the
 * "drivers" send {@code input} actions as the current holders of each control — steering towards a
 * point a few tiles ahead, slowing down for bends, honking at geese, wiping in the rain, switching
 * the headlights on before tunnels and pressing again after every SEAT SHUFFLE.
 */
final class Autopilot {

    private static final List<Control> HOLDS =
            List.of(Control.STEER_LEFT, Control.STEER_RIGHT, Control.GAS, Control.BRAKE);
    private static final double LOOKAHEAD = 2.2;

    private final GameHarness game;
    private final List<double[]> path = new ArrayList<>();
    private final List<String> rows;
    private final List<int[]> cones = new ArrayList<>();
    private final Map<Control, Boolean> sent = new EnumMap<>(Control.class);
    private int progress;
    private int rolesVersion = -1;
    private long lastHonk = Long.MIN_VALUE / 2;
    private long lastWipe = Long.MIN_VALUE / 2;
    private boolean lights;
    private double stuckX;
    private double stuckY;
    private long stuckSince;
    private long reverseUntil = Long.MIN_VALUE / 2;

    /** Plans the path of the level that is running now. */
    @SuppressWarnings("unchecked")
    Autopilot(GameHarness game) {
        this.game = game;
        String navigator = TestGames.navigator(game);
        Map<String, Object> map = (Map<String, Object>) level(game.view(navigator)).get("map");
        this.rows = (List<String>) map.get("rows");
        for (List<Integer> cone : (List<List<Integer>>) map.get("cones")) {
            cones.add(new int[] {cone.get(0), cone.get(1)});
        }
        Map<String, Object> start = (Map<String, Object>) game.debugState().get("start");
        plan((Integer) start.get("x"), (Integer) start.get("y"));
    }

    /**
     * Drives until the level is over or {@code seconds} have passed.
     *
     * @return the virtual seconds it took
     */
    double drive(double seconds) {
        long started = game.now();
        while (game.phase() == RunPhase.PLAYING && game.now() - started < seconds * 1000) {
            step();
            game.advance(Duration.ofMillis(LevelRun.TICK_MILLIS));
        }
        return (game.now() - started) / 1000.0;
    }

    @SuppressWarnings("unchecked")
    private void step() {
        Map<String, Object> state = game.debugState();
        int version = (Integer) state.get("rolesVersion");
        if (version != rolesVersion) {
            rolesVersion = version;
            sent.clear(); // a shuffle released everything
            lights = Boolean.TRUE.equals(state.get("headlights"));
        }
        Map<String, Double> bus = (Map<String, Double>) state.get("bus");
        double x = bus.get("x");
        double y = bus.get("y");
        double heading = bus.get("heading");
        double speed = bus.get("speed");
        advanceProgress(x, y);

        double[] target = pointAhead(x, y, LOOKAHEAD);
        double error = angle(Math.atan2(target[1] - y, target[0] - x) - heading);
        double[] far = pointAhead(x, y, 5.5);
        double bend = Math.abs(angle(Math.atan2(far[1] - y, far[0] - x) - heading));
        double sharp = Math.max(Math.abs(error), bend);
        double wanted = sharp > 1.0 ? 1.6 : sharp > 0.7 ? 2.4 : sharp > 0.4 ? 3.6 : 6;

        long now = game.now();
        if (Math.hypot(x - stuckX, y - stuckY) > 0.3) {
            stuckX = x;
            stuckY = y;
            stuckSince = now;
        } else if (now - stuckSince > 1000 && now > reverseUntil) {
            reverseUntil = now + 900; // stuck against something: back up and try again
            stuckSince = now + 900;
        }
        boolean reversing = now < reverseUntil;
        Map<Control, Boolean> want = new EnumMap<>(Control.class);
        want.put(Control.STEER_LEFT, reversing ? error > 0.06 : error < -0.06);
        want.put(Control.STEER_RIGHT, reversing ? error < -0.06 : error > 0.06);
        want.put(Control.GAS, !reversing && speed < wanted - 0.2);
        want.put(Control.BRAKE, reversing || speed > wanted + 0.6);
        for (Control control : HOLDS) {
            boolean pressed = want.get(control);
            if (sent.getOrDefault(control, false) != pressed) {
                holders(game, control)
                        .forEach(id -> game.act(id, "input", input(control, pressed)));
                sent.put(control, pressed);
            }
        }
        utilities(state, x, y);
    }

    @SuppressWarnings("unchecked")
    private void utilities(Map<String, Object> state, double x, double y) {
        long now = game.now();
        Map<String, Object> hazards = (Map<String, Object>) state.get("hazards");
        for (Map<String, Object> flock : (List<Map<String, Object>>) hazards.get("geese")) {
            if (!"GONE".equals(flock.get("state")) && now - lastHonk > 400) {
                for (List<Integer> cell : (List<List<Integer>>) flock.get("cells")) {
                    if (Math.hypot(cell.get(0) + 0.5 - x, cell.get(1) + 0.5 - y) < 5.8) {
                        tap(Control.HORN);
                        lastHonk = now;
                        break;
                    }
                }
            }
        }
        if (cell(x, y) == '~' && now - lastWipe > 1000) {
            tap(Control.WIPERS);
            lastWipe = now;
        }
        double[] soon = pointAhead(x, y, 3);
        if (!lights && (cell(soon[0], soon[1]) == 't' || cell(x, y) == 't')) {
            tap(Control.HEADLIGHTS);
            lights = true;
        }
    }

    private void tap(Control control) {
        for (String id : holders(game, control)) {
            game.act(id, "input", input(control, true));
            game.act(id, "input", input(control, false));
        }
    }

    private char cell(double x, double y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        return cy >= 0 && cy < rows.size() && cx >= 0 && cx < rows.get(cy).length()
                ? rows.get(cy).charAt(cx)
                : '#';
    }

    private void advanceProgress(double x, double y) {
        int best = progress;
        double bestDistance = Double.MAX_VALUE;
        for (int i = progress; i < Math.min(path.size(), progress + 12); i++) {
            double d = Math.hypot(path.get(i)[0] - x, path.get(i)[1] - y);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        progress = best;
    }

    private double[] pointAhead(double x, double y, double distance) {
        for (int i = progress; i < path.size(); i++) {
            if (Math.hypot(path.get(i)[0] - x, path.get(i)[1] - y) >= distance) {
                return path.get(i);
            }
        }
        return path.getLast();
    }

    private static double angle(double radians) {
        double a = radians % (2 * Math.PI);
        return a > Math.PI ? a - 2 * Math.PI : a < -Math.PI ? a + 2 * Math.PI : a;
    }

    private boolean paved(int x, int y) {
        char c =
                y >= 0 && y < rows.size() && x >= 0 && x < rows.get(y).length()
                        ? rows.get(y).charAt(x)
                        : '#';
        return c == 'r' || c == '~' || c == 't' || c == 'P';
    }

    /** Dijkstra over paved cells; cells next to anything else cost more (keep to the middle). */
    private void plan(int sx, int sy) {
        int w = rows.getFirst().length();
        int h = rows.size();
        double[] dist = new double[w * h];
        int[] prev = new int[w * h];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist[sy * w + sx] = 0;
        queue.add(new double[] {0, sy * w + sx});
        int goal = -1;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int index = (int) top[1];
            if (top[0] > dist[index]) {
                continue;
            }
            int cx = index % w;
            int cy = index / w;
            if (rows.get(cy).charAt(cx) == 'P') {
                goal = index;
                break;
            }
            for (int[] step : steps) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!paved(nx, ny)) {
                    continue;
                }
                double cost = 1 + 2.5 * edgeCells(nx, ny) + hazardCost(nx, ny);
                int next = ny * w + nx;
                if (dist[index] + cost < dist[next]) {
                    dist[next] = dist[index] + cost;
                    prev[next] = index;
                    queue.add(new double[] {dist[next], next});
                }
            }
        }
        List<double[]> reversed = new ArrayList<>();
        for (int i = goal; i >= 0; i = prev[i]) {
            reversed.add(new double[] {i % w + 0.5, i / w + 0.5});
        }
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        for (int i = 0; i < 3; i++) { // aim into the lot, not at its edge
            double[] last = path.getLast();
            double[] before = path.get(path.size() - 2);
            path.add(new double[] {2 * last[0] - before[0], 2 * last[1] - before[1]});
        }
    }

    /** Cones (and the cells next to them) and the CEO's cars are worth a detour. */
    private double hazardCost(int x, int y) {
        double cost = 0;
        for (int[] cone : cones) {
            int d = Math.abs(cone[0] - x) + Math.abs(cone[1] - y);
            cost += d == 0 ? 8 : d == 1 ? 1.5 : 0;
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (charAt(x + dx, y + dy) == 'E') {
                    cost += 3;
                }
            }
        }
        return cost;
    }

    private char charAt(int x, int y) {
        return y >= 0 && y < rows.size() && x >= 0 && x < rows.get(y).length()
                ? rows.get(y).charAt(x)
                : '#';
    }

    private int edgeCells(int x, int y) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && !paved(x + dx, y + dy)) {
                    count++;
                }
            }
        }
        return Math.min(count, 1);
    }
}
