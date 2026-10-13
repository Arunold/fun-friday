package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.games.snakearena.SnakeArenaView.FrameView;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared set-up for the Snake Arena scenario tests: a {@link GameHarness} plus direct access to the
 * round's {@link Simulation}, so a test can lay snakes, food and power-ups exactly where it needs
 * them. Unless a test says otherwise there is one round and no power-ups.
 *
 * <p>Snakes can also be told to {@linkplain #circle circle} a rectangle: before every tick the
 * arena then sends the {@code turn} actions a player would, which keeps them alive (and their
 * points under control) for as long as a test needs.
 */
final class TestArena {

    private static final long DEFAULT_SEED = 7;

    final GameHarness game;
    private final SnakeArenaLogic[] logic;
    private final Map<String, int[]> loops = new LinkedHashMap<>();

    private TestArena(GameHarness game, SnakeArenaLogic[] logic) {
        this.game = game;
        this.logic = logic;
    }

    /** Starts a game with these settings on top of the test defaults and this random seed. */
    static TestArena start(Map<String, ?> settings, long seed, String... players) {
        SnakeArenaLogic[] holder = new SnakeArenaLogic[1];
        GameDefinition definition =
                new SnakeArenaGame() {
                    @Override
                    public GameLogic createLogic(GameSettings values) {
                        holder[0] = (SnakeArenaLogic) super.createLogic(values);
                        return holder[0];
                    }
                };
        Map<String, Object> values = new HashMap<>();
        values.put("rounds", 1);
        values.put("powerUps", false);
        values.putAll(settings);
        GameHarness game =
                GameHarness.of(definition).players(players).settings(values).seed(seed).start();
        return new TestArena(game, holder);
    }

    /** Starts a game with these settings on top of the test defaults. */
    static TestArena start(Map<String, ?> settings, String... players) {
        return start(settings, DEFAULT_SEED, players);
    }

    /** Starts a one-round classic game without power-ups. */
    static TestArena start(String... players) {
        return start(Map.of(), players);
    }

    Simulation sim() {
        return logic[0].simulation();
    }

    Board board() {
        return sim().board();
    }

    int cell(int x, int y) {
        return board().cell(x, y);
    }

    Snake snake(String id) {
        return sim().snake(id);
    }

    /** No automatic food and an empty board, so nothing random gets in the way. */
    TestArena calm() {
        sim().setFoodTarget(0);
        board().clearFood();
        return this;
    }

    /** Runs through the start delay: the next tick moves the snakes. */
    TestArena go() {
        return ticks(Simulation.START_DELAY_TICKS - (int) sim().tick());
    }

    /** Lets {@code count} ticks pass (steering the circling snakes before each one). */
    TestArena ticks(int count) {
        if (loops.isEmpty()) {
            game.advance(Duration.ofMillis(count * SnakeArenaLogic.TICK_MS));
            return this;
        }
        for (int i = 0; i < count && game.phase() == RunPhase.PLAYING; i++) {
            steer();
            game.advance(Duration.ofMillis(SnakeArenaLogic.TICK_MS));
        }
        return this;
    }

    /** Lets ticks pass until the round's tick counter reaches {@code tick}. */
    TestArena until(long tick) {
        return ticks((int) (tick - sim().tick()));
    }

    /** Plays on tick by tick (circling snakes keep circling) until the round is over. */
    TestArena playOut() {
        while (game.phase() == RunPhase.PLAYING) {
            ticks(1);
        }
        return this;
    }

    /**
     * From now on keeps a snake running clockwise along the edge of the rectangle (x0, y0)–(x1,
     * y1), turning at its corners. Place the snake on that edge first.
     */
    TestArena circle(String id, int x0, int y0, int x1, int y1) {
        loops.put(id, new int[] {x0, y0, x1, y1});
        return this;
    }

    /** Stops steering a circling snake: it goes straight on. */
    TestArena release(String id) {
        loops.remove(id);
        return this;
    }

    /** Lays a snake on exactly these cells: x and y of the head first, then of the body. */
    TestArena place(String id, Dir dir, int... xy) {
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            cells.add(cell(xy[i], xy[i + 1]));
        }
        sim().place(id, cells, dir);
        return this;
    }

    TestArena food(int x, int y) {
        board().addFood(cell(x, y));
        return this;
    }

    TestArena powerUp(int x, int y, PowerType type) {
        board().addPowerUp(new Board.PowerUp(cell(x, y), type, sim().tick() + 1000));
        return this;
    }

    /** Sends a {@code turn} action. */
    TestArena turn(String id, String dir) {
        game.act(id, SnakeArenaLogic.TURN, Map.of("dir", dir));
        return this;
    }

    /** Sends a {@code turn} action for the head position of tick {@code at}, as browsers do. */
    TestArena turn(String id, String dir, long at) {
        game.act(id, SnakeArenaLogic.TURN, Map.of("dir", dir, "at", at));
        return this;
    }

    /** The head of a snake as {x, y}. */
    int[] head(String id) {
        int head = snake(id).head();
        return new int[] {board().x(head), board().y(head)};
    }

    /** The cells of a snake as x, y pairs, head first. */
    List<List<Integer>> body(String id) {
        List<List<Integer>> cells = new ArrayList<>();
        for (int cell : snake(id).body()) {
            cells.add(List.of(board().x(cell), board().y(cell)));
        }
        return cells;
    }

    /** The frame in the spectators' view (what every browser draws after a snapshot). */
    @SuppressWarnings("unchecked")
    Map<String, Object> frame() {
        return (Map<String, Object>) game.spectatorView().get("frame");
    }

    /** A snake of the frame in the view. */
    @SuppressWarnings("unchecked")
    Map<String, Object> frameSnake(String id) {
        return ((List<Map<String, Object>>) frame().get("snakes"))
                .stream().filter(snake -> id.equals(snake.get("id"))).findFirst().orElseThrow();
    }

    /** The kill feed of the frame in the view, oldest first. */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> feed() {
        return (List<Map<String, Object>>) frame().get("feed");
    }

    /** Every frame streamed so far. */
    List<FrameView> streamedFrames() {
        return game.streamed().stream()
                .filter(event -> SnakeArenaLogic.FRAME_EVENT.equals(event.type()))
                .map(event -> (FrameView) event.payload())
                .toList();
    }

    /** Sends the turn each circling snake needs to stay on its rectangle. */
    private void steer() {
        for (Map.Entry<String, int[]> loop : loops.entrySet()) {
            Snake snake = snake(loop.getKey());
            if (snake == null || !snake.alive()) {
                continue;
            }
            Dir wanted =
                    clockwise(loop.getValue(), board().x(snake.head()), board().y(snake.head()));
            if (wanted != snake.dir()) {
                turn(loop.getKey(), wanted.name());
            }
        }
    }

    /** The heading that follows the rectangle's edge clockwise from (x, y). */
    private static Dir clockwise(int[] rect, int x, int y) {
        if (y == rect[1] && x < rect[2]) {
            return Dir.RIGHT;
        }
        if (x == rect[2] && y < rect[3]) {
            return Dir.DOWN;
        }
        if (y == rect[3] && x > rect[0]) {
            return Dir.LEFT;
        }
        return Dir.UP;
    }
}
