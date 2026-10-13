package com.example.funfridaygame.games.snakearena;

import java.util.List;

/**
 * What a browser receives for Snake Arena. Everything is public: players and spectators get the
 * same view. Cells are numbered {@code y · cols + x}; times are ticks of {@code tickMs}
 * milliseconds since the round started.
 *
 * @param cols columns of the grid
 * @param rows rows of the grid
 * @param arena SMALL, MEDIUM or LARGE
 * @param variation {@code classic}, {@code shrinking} or {@code last-standing}
 * @param powers whether power-ups are on
 * @param tickMs milliseconds per tick
 * @param startDelay ticks at the start of a round during which nothing moves
 * @param respawnTicks ticks between a crash and the respawn (0 when snakes don't come back)
 * @param shrink when and how far the walls move (shrinking arena only)
 * @param roster everyone who started the round (names stay known after someone leaves)
 * @param frame the latest frame, also streamed as {@code frame} on every tick
 * @param results the round's results (revealed only), best first
 */
public record SnakeArenaView(
        int cols,
        int rows,
        String arena,
        String variation,
        boolean powers,
        int tickMs,
        int startDelay,
        int respawnTicks,
        ShrinkView shrink,
        List<RosterEntry> roster,
        FrameView frame,
        List<ResultRow> results) {

    /**
     * The schedule of the shrinking arena: the walls move in by one cell on every side at tick
     * {@code from}, {@code from + every}, … while the playable area stays at least {@code minWidth}
     * × {@code minHeight}.
     *
     * @param from tick of the first wall move
     * @param every ticks between wall moves
     * @param minWidth narrowest playable width
     * @param minHeight lowest playable height
     */
    public record ShrinkView(int from, int every, int minWidth, int minHeight) {}

    /**
     * The state of the arena after a tick (stream event {@code frame}).
     *
     * @param round the round it belongs to (frames of a new round restart at tick 0)
     * @param tick ticks since the round started
     * @param snakes every snake in the round, in join order
     * @param food cells with food
     * @param powerUps power-ups lying on the board
     * @param bounds the walls (they move in the shrinking arena)
     * @param feed the last crashes, oldest first
     */
    public record FrameView(
            int round,
            int tick,
            List<SnakeView> snakes,
            int[] food,
            List<PowerUpView> powerUps,
            BoundsView bounds,
            List<FeedEntry> feed) {}

    /**
     * A snake.
     *
     * @param id the player's id
     * @param body cells head first (empty while crashed)
     * @param dir heading: UP, DOWN, LEFT or RIGHT
     * @param alive whether it is on the board
     * @param respawnAt tick of the respawn (crashed snakes that come back only)
     * @param power BOOST, GHOST or MAGNET while active
     * @param powerUntil the last tick of the power-up
     * @param points round points so far
     * @param length current length (0 while crashed)
     */
    public record SnakeView(
            String id,
            int[] body,
            String dir,
            boolean alive,
            Integer respawnAt,
            String power,
            Integer powerUntil,
            int points,
            int length) {}

    /**
     * A power-up on the board.
     *
     * @param cell where it lies
     * @param type BOOST, GHOST or MAGNET
     * @param until the tick at which it vanishes
     */
    public record PowerUpView(int cell, String type, int until) {}

    /**
     * The playable rectangle (inclusive); everything outside is wall.
     *
     * @param x0 leftmost playable column
     * @param y0 topmost playable row
     * @param x1 rightmost playable column
     * @param y1 bottom playable row
     */
    public record BoundsView(int x0, int y0, int x1, int y1) {}

    /**
     * A line of the kill feed.
     *
     * @param id increasing within the round
     * @param kind BODY ({@code b} ran into {@code a}), HEAD, WALL, SELF or LEFT (see {@link
     *     KillFeed.Kind})
     * @param a the first player
     * @param b the second player (BODY and HEAD only)
     */
    public record FeedEntry(int id, String kind, String a, String b) {}

    /**
     * A player of the round.
     *
     * @param id player id
     * @param name display name
     * @param color CSS colour
     */
    public record RosterEntry(String id, String name, String color) {}

    /**
     * A player's round.
     *
     * @param playerId who
     * @param points round points including bonuses
     * @param food pieces of food eaten
     * @param credits snakes that crashed into their body (+5 each)
     * @param crashes how often they crashed
     * @param maxLength the longest they were
     * @param longest whether they earned the longest-snake bonus
     * @param survivor whether they survived (last snake standing)
     */
    public record ResultRow(
            String playerId,
            int points,
            int food,
            int credits,
            int crashes,
            int maxLength,
            boolean longest,
            boolean survivor) {}
}
