package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.games.snakearena.Board.PowerUp;
import com.example.funfridaygame.games.snakearena.MoveResolver.Crash;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.BoundsView;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.FrameView;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.PowerUpView;
import com.example.funfridaygame.games.snakearena.SnakeArenaView.SnakeView;
import com.example.funfridaygame.games.snakearena.Spawner.Placement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The grid simulation of one round, advanced one tick at a time by {@link #step(long)}.
 *
 * <p>Per tick: crashed snakes whose {@value #RESPAWN_TICKS} ticks are up respawn; boosted snakes
 * take an extra step, then every snake moves one cell ({@link MoveResolver}); crashed snakes turn
 * into food (every second body cell, at most {@value #CRASH_FOOD_CAP} pieces) and credit the snake
 * they ran into; magnets pull food; shrinking walls move in; power-ups appear, run out and vanish;
 * eaten food is replaced. Nothing moves during the first {@value #START_DELAY_TICKS} ticks, so
 * everybody can find their snake. All randomness comes from the game's generator, so a seeded game
 * replays identically.
 */
final class Simulation {

    /** Ticks at the start of a round during which nothing moves. */
    static final int START_DELAY_TICKS = 10;

    /** Ticks between a crash and the respawn (3 s). */
    static final int RESPAWN_TICKS = 30;

    /** The walls start closing in this many ticks before the round ends (30 s). */
    static final int SHRINK_LEAD_TICKS = 300;

    /** Ticks between two wall moves (3 s). */
    static final int SHRINK_EVERY_TICKS = 30;

    /** The shrinking arena never gets narrower than this. */
    static final int MIN_WIDTH = 12;

    /** The shrinking arena never gets lower than this. */
    static final int MIN_HEIGHT = 8;

    /** Points for the owner of a body another snake crashed into. */
    static final int BODY_CREDIT = 5;

    /** Food pieces a crashed snake leaves at most. */
    static final int CRASH_FOOD_CAP = 30;

    /** Power-ups on the board at most. */
    static final int MAX_POWER_UPS = 2;

    /** Ticks a power-up lies on the board before it vanishes (15 s). */
    static final int POWER_UP_LIFETIME = 150;

    /** Shortest gap between two power-ups (10 s). */
    static final int POWER_UP_MIN_GAP = 100;

    /** Longest gap between two power-ups (15 s). */
    static final int POWER_UP_MAX_GAP = 150;

    /** Reach of a magnet in cells. */
    static final int MAGNET_RADIUS = 5;

    /**
     * A turn may be scheduled at most this many ticks ahead: browsers far from the server draw
     * their own snake further ahead (see the README's "Latency").
     */
    static final int MAX_TURN_LEAD = 4;

    /** Power-ups never appear this close to a head (no instant pick-ups). */
    private static final int POWER_UP_HEAD_DISTANCE = 2;

    private final Board board;
    private final Variation variation;
    private final boolean powerUps;
    private final long shrinkFrom;
    private final RandomGenerator random;
    private final Map<String, Snake> snakes = new LinkedHashMap<>();
    private final KillFeed feed = new KillFeed();
    private int startedWith;
    private int foodTarget = -1;
    private long tick;
    private long nextPowerUpAt = Long.MAX_VALUE;

    /**
     * A round's simulation.
     *
     * @param board the empty arena
     * @param variation the game mode
     * @param powerUps whether power-ups appear
     * @param shrinkFrom tick of the first wall move, {@code -1} if the walls never move
     * @param random the game's random generator
     */
    Simulation(
            Board board,
            Variation variation,
            boolean powerUps,
            long shrinkFrom,
            RandomGenerator random) {
        this.board = board;
        this.variation = variation;
        this.powerUps = powerUps;
        this.shrinkFrom = shrinkFrom;
        this.random = random;
    }

    // ------------------------------------------------------------------ set-up & input

    /** Puts the players' snakes on the start slots (in this order) and lays out the food. */
    void start(List<String> playerIds) {
        List<Placement> slots = Spawner.startSlots(board, playerIds.size());
        for (int i = 0; i < playerIds.size(); i++) {
            Snake snake = new Snake(playerIds.get(i));
            snake.spawn(slots.get(i).cells(), slots.get(i).dir(), 0);
            snakes.put(snake.id(), snake);
        }
        startedWith = snakes.size();
        boolean placed = true;
        while (placed && board.foodCount() < foodTarget()) {
            placed = spawnFood();
        }
        if (powerUps) {
            nextPowerUpAt = powerUpGap();
        }
    }

    /**
     * Queues a turn for a player's snake; harmless turns are ignored silently.
     *
     * @param from the tick whose head position the turn starts from, i.e. the cell where the
     *     player's browser shows the snake turning ({@code -1}: the next move); at most {@value
     *     #MAX_TURN_LEAD} ticks ahead
     */
    void turn(String playerId, Dir dir, long from) {
        Snake snake = snakes.get(playerId);
        if (snake != null) {
            snake.queue(dir, Math.min(from, tick + MAX_TURN_LEAD));
        }
    }

    /** A player left for good: their snake turns into food and is gone. */
    void remove(String playerId) {
        Snake snake = snakes.remove(playerId);
        if (snake == null) {
            return;
        }
        boolean wasAlive = snake.alive();
        List<Integer> remains = snake.vanish();
        if (wasAlive) {
            dropFood(remains);
        }
        feed.add(KillFeed.Kind.LEFT, playerId, null);
    }

    // ------------------------------------------------------------------ the tick

    /** Advances the arena to tick {@code now}. */
    void step(long now) {
        tick = now;
        respawnDue();
        if (tick > START_DELAY_TICKS) {
            move(true);
            move(false);
            pullFood();
        }
        shrinkWalls();
        managePowerUps();
        for (Snake snake : snakes.values()) {
            snake.expirePower(tick);
        }
        if (board.foodCount() < foodTarget()) {
            spawnFood();
        }
    }

    /** Last snake standing: at most one snake left (none when the round started with one). */
    boolean isOver() {
        if (variation != Variation.LAST_STANDING) {
            return false;
        }
        long alive = snakes.values().stream().filter(Snake::alive).count();
        return startedWith >= 2 ? alive <= 1 : alive == 0;
    }

    private void move(boolean boostedOnly) {
        List<Snake> alive = alive();
        List<Snake> movers =
                alive.stream()
                        .filter(s -> s.movesAt(tick) && (!boostedOnly || s.has(PowerType.BOOST)))
                        .toList();
        if (movers.isEmpty()) {
            return;
        }
        List<Crash> crashes = MoveResolver.step(board, alive, movers, tick);
        Set<String> headOns = new HashSet<>();
        List<List<Integer>> remains = new ArrayList<>();
        for (Crash crash : crashes) {
            report(crash, headOns);
            remains.add(crash.snake().crash(respawnTick()));
        }
        remains.forEach(this::dropFood);
    }

    /** Credits the body's owner and writes the kill feed (one line per head-on pair). */
    private void report(Crash crash, Set<String> headOns) {
        String id = crash.snake().id();
        switch (crash.cause()) {
            case BODY -> {
                crash.other().credit(BODY_CREDIT);
                feed.add(KillFeed.Kind.BODY, crash.other().id(), id);
            }
            case HEAD -> {
                String other = crash.other().id();
                String pair = id.compareTo(other) < 0 ? id + "|" + other : other + "|" + id;
                if (headOns.add(pair)) {
                    feed.add(KillFeed.Kind.HEAD, id, other);
                }
            }
            case WALL -> feed.add(KillFeed.Kind.WALL, id, null);
            default -> feed.add(KillFeed.Kind.SELF, id, null);
        }
    }

    private long respawnTick() {
        return variation.respawns() ? tick + RESPAWN_TICKS : -1;
    }

    private void respawnDue() {
        for (Snake snake : snakes.values()) {
            if (snake.alive() || snake.respawnAt() < 0 || tick < snake.respawnAt()) {
                continue;
            }
            Optional<Placement> spot = Spawner.respawn(board, occupied(), alive(), random);
            if (spot.isPresent()) {
                for (int cell : spot.get().cells()) {
                    board.removeFood(cell);
                    board.takePowerUp(cell);
                }
                snake.spawn(spot.get().cells(), spot.get().dir(), tick);
            }
        }
    }

    /** Every second cell of a crashed body becomes food (at most {@value #CRASH_FOOD_CAP}). */
    private void dropFood(List<Integer> cells) {
        boolean[] taken = occupied();
        int step = Math.max(2, Math.ceilDiv(cells.size(), CRASH_FOOD_CAP));
        for (int i = 0; i < cells.size(); i += step) {
            int cell = cells.get(i);
            if (board.inside(cell) && !taken[cell] && board.powerUpAt(cell) == null) {
                board.addFood(cell);
            }
        }
    }

    /** Magnets pull every piece of food within reach one cell towards the nearest magnet head. */
    private void pullFood() {
        List<Snake> magnets = alive().stream().filter(s -> s.has(PowerType.MAGNET)).toList();
        if (magnets.isEmpty() || board.foodCount() == 0) {
            return;
        }
        boolean[] taken = occupied();
        List<Integer> pieces = new ArrayList<>(board.food());
        pieces.sort(
                Comparator.comparingInt((Integer cell) -> distance2(cell, nearest(cell, magnets)))
                        .thenComparingInt(cell -> cell));
        int reach = MAGNET_RADIUS * MAGNET_RADIUS;
        for (int piece : pieces) {
            Snake magnet = nearest(piece, magnets);
            if (distance2(piece, magnet.head()) <= reach) {
                int target = pullTarget(piece, magnet.head(), taken);
                if (target >= 0) {
                    board.moveFood(piece, target);
                }
            }
        }
    }

    private Snake nearest(int cell, List<Snake> magnets) {
        Snake best = magnets.getFirst();
        for (Snake magnet : magnets) {
            if (distance2(cell, magnet.head()) < distance2(cell, best.head())) {
                best = magnet;
            }
        }
        return best;
    }

    private int distance2(int cell, Snake snake) {
        return distance2(cell, snake.head());
    }

    private int distance2(int a, int b) {
        int dx = board.x(a) - board.x(b);
        int dy = board.y(a) - board.y(b);
        return dx * dx + dy * dy;
    }

    /** The free cell one step from {@code piece} towards {@code head} (longer axis first). */
    private int pullTarget(int piece, int head, boolean[] taken) {
        int x = board.x(piece);
        int y = board.y(piece);
        int dx = board.x(head) - x;
        int dy = board.y(head) - y;
        int[][] steps =
                Math.abs(dx) >= Math.abs(dy)
                        ? new int[][] {{Integer.signum(dx), 0}, {0, Integer.signum(dy)}}
                        : new int[][] {{0, Integer.signum(dy)}, {Integer.signum(dx), 0}};
        for (int[] step : steps) {
            if (step[0] == 0 && step[1] == 0) {
                continue;
            }
            int tx = x + step[0];
            int ty = y + step[1];
            if (board.inside(tx, ty)) {
                int target = board.cell(tx, ty);
                if (!taken[target] && board.isClear(target)) {
                    return target;
                }
            }
        }
        return -1;
    }

    /** Shrinking arena: every few seconds the walls move in; whatever lies under them is lost. */
    private void shrinkWalls() {
        if (shrinkFrom < 0 || tick < shrinkFrom || (tick - shrinkFrom) % SHRINK_EVERY_TICKS != 0) {
            return;
        }
        Bounds next = board.bounds().shrink(MIN_WIDTH, MIN_HEIGHT);
        if (next.equals(board.bounds())) {
            return;
        }
        board.setBounds(next);
        List<List<Integer>> remains = new ArrayList<>();
        for (Snake snake : alive()) {
            if (snake.body().stream().anyMatch(cell -> !board.inside(cell))) {
                feed.add(KillFeed.Kind.WALL, snake.id(), null);
                remains.add(snake.crash(respawnTick()));
            }
        }
        remains.forEach(this::dropFood);
    }

    private void managePowerUps() {
        board.expirePowerUps(tick);
        if (!powerUps || tick < nextPowerUpAt) {
            return;
        }
        nextPowerUpAt = tick + powerUpGap();
        if (board.powerUps().size() >= MAX_POWER_UPS) {
            return;
        }
        boolean[] blocked = occupied();
        for (Snake snake : alive()) {
            block(blocked, snake.head(), POWER_UP_HEAD_DISTANCE);
        }
        int cell = board.randomFreeCell(random, 2, c -> !blocked[c]);
        if (cell >= 0) {
            PowerType[] types = PowerType.values();
            PowerType type = types[random.nextInt(types.length)];
            board.addPowerUp(new PowerUp(cell, type, tick + POWER_UP_LIFETIME));
        }
    }

    private void block(boolean[] blocked, int center, int radius) {
        int cx = board.x(center);
        int cy = board.y(center);
        for (int y = Math.max(0, cy - radius); y <= Math.min(board.rows() - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius);
                    x <= Math.min(board.cols() - 1, cx + radius);
                    x++) {
                blocked[board.cell(x, y)] = true;
            }
        }
    }

    private long powerUpGap() {
        return POWER_UP_MIN_GAP + random.nextInt(POWER_UP_MAX_GAP - POWER_UP_MIN_GAP + 1);
    }

    private boolean spawnFood() {
        boolean[] taken = occupied();
        int cell = board.randomFreeCell(random, 0, c -> !taken[c]);
        if (cell < 0) {
            return false;
        }
        board.addFood(cell);
        return true;
    }

    /** About 2 + 1.5 pieces per player. */
    private int foodTarget() {
        return foodTarget >= 0 ? foodTarget : (int) Math.round(2 + 1.5 * snakes.size());
    }

    private List<Snake> alive() {
        return snakes.values().stream().filter(Snake::alive).toList();
    }

    /** Cells taken by snakes. */
    private boolean[] occupied() {
        boolean[] taken = new boolean[board.size()];
        for (Snake snake : snakes.values()) {
            for (int cell : snake.body()) {
                taken[cell] = true;
            }
        }
        return taken;
    }

    // ------------------------------------------------------------------ queries

    Board board() {
        return board;
    }

    long tick() {
        return tick;
    }

    /** The snakes still in the round, in join order. */
    Collection<Snake> snakes() {
        return Collections.unmodifiableCollection(snakes.values());
    }

    /** A player's snake, or {@code null} once they left. */
    Snake snake(String playerId) {
        return snakes.get(playerId);
    }

    /** The compact, public state of the arena after the current tick. */
    FrameView frame(int round) {
        List<SnakeView> views = new ArrayList<>(snakes.size());
        for (Snake snake : snakes.values()) {
            views.add(view(snake));
        }
        int[] food = board.food().stream().mapToInt(Integer::intValue).toArray();
        List<PowerUpView> items =
                board.powerUps().stream()
                        .map(p -> new PowerUpView(p.cell(), p.type().name(), (int) p.until()))
                        .toList();
        Bounds b = board.bounds();
        return new FrameView(
                round,
                (int) tick,
                views,
                food,
                items,
                new BoundsView(b.x0(), b.y0(), b.x1(), b.y1()),
                feed.entries());
    }

    private static SnakeView view(Snake snake) {
        boolean waiting = !snake.alive() && snake.respawnAt() >= 0;
        PowerType power = snake.power();
        return new SnakeView(
                snake.id(),
                snake.body().stream().mapToInt(Integer::intValue).toArray(),
                snake.dir().name(),
                snake.alive(),
                waiting ? Integer.valueOf((int) snake.respawnAt()) : null,
                power == null ? null : power.name(),
                power == null ? null : Integer.valueOf((int) snake.powerUntil()),
                snake.points(),
                snake.length());
    }

    // ------------------------------------------------------------------ test hooks

    /** Test hook: lays a snake on exactly these cells (head first), keeping its power-up. */
    void place(String playerId, List<Integer> cells, Dir dir) {
        snakes.get(playerId).place(cells, dir);
    }

    /** Test hook: a fixed amount of food instead of 2 + 1.5 per player. */
    void setFoodTarget(int target) {
        foodTarget = target;
    }
}
