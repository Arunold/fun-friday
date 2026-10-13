package com.example.funfridaygame.games.snakearena;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * One player's snake during a round: its body (head first), heading, queued turns, active power-up
 * and the round's statistics. The same object lives through crashes and respawns; a new round
 * starts with new snakes.
 */
final class Snake {

    /** Turns a player may queue ahead, so quick double-turns work. */
    static final int MAX_QUEUED_TURNS = 2;

    /**
     * A queued turn.
     *
     * @param dir the new heading
     * @param from the tick whose head position the turn starts from; it is carried out by the first
     *     move after that tick ({@code -1}: by the next move)
     */
    private record Turn(Dir dir, long from) {}

    private final String id;
    private final Deque<Integer> body = new ArrayDeque<>();
    private final Deque<Turn> turns = new ArrayDeque<>();
    private Dir dir = Dir.RIGHT;
    private boolean alive;
    private long respawnAt = -1;
    private long spawnedAt;
    private PowerType power;
    private long powerUntil;
    private int points;
    private int food;
    private int credits;
    private int crashes;
    private int maxLength;

    Snake(String id) {
        this.id = id;
    }

    /** The player's id. */
    String id() {
        return id;
    }

    boolean alive() {
        return alive;
    }

    /** The heading of the last move (or the start heading). */
    Dir dir() {
        return dir;
    }

    int head() {
        return body.getFirst();
    }

    int length() {
        return body.size();
    }

    /** The body cells, head first (empty while crashed). */
    Collection<Integer> body() {
        return Collections.unmodifiableCollection(body);
    }

    /** The tick at which a crashed snake comes back ({@code -1}: never or not crashed). */
    long respawnAt() {
        return respawnAt;
    }

    /** The active power-up, or {@code null}. */
    PowerType power() {
        return power;
    }

    /** The last tick of the active power-up. */
    long powerUntil() {
        return powerUntil;
    }

    /** Whether the snake currently has this power-up. */
    boolean has(PowerType type) {
        return power == type;
    }

    /** Round points so far (food, credits and, after the round, bonuses). */
    int points() {
        return points;
    }

    /** Pieces of food eaten this round. */
    int food() {
        return food;
    }

    /** How many snakes crashed into this one's body this round. */
    int credits() {
        return credits;
    }

    /** Crashes this round. */
    int crashes() {
        return crashes;
    }

    /** The longest this snake has been this round. */
    int maxLength() {
        return maxLength;
    }

    // ------------------------------------------------------------------ life cycle

    /**
     * Puts the snake on the board at the start of a round or after a crash. It doesn't move during
     * the tick it appears in.
     */
    void spawn(List<Integer> cells, Dir heading, long tick) {
        place(cells, heading);
        respawnAt = -1;
        spawnedAt = tick;
        power = null;
    }

    /** Lays the body on the given cells (head first), alive, with no queued turns. */
    void place(List<Integer> cells, Dir heading) {
        body.clear();
        body.addAll(cells);
        turns.clear();
        dir = heading;
        alive = true;
        maxLength = Math.max(maxLength, body.size());
    }

    /** Whether the snake moves during this tick (alive and not just (re)spawned). */
    boolean movesAt(long tick) {
        return alive && spawnedAt < tick;
    }

    /**
     * Queues a turn. Silently ignored (returns {@code false}) while crashed, when the queue is full
     * and for the direction of the last queued turn (or the current heading) and its reverse.
     *
     * @param turn the new heading
     * @param from the tick whose head position the turn starts from ({@code -1}: the next move)
     */
    boolean queue(Dir turn, long from) {
        if (!alive || turns.size() >= MAX_QUEUED_TURNS) {
            return false;
        }
        Dir last = turns.isEmpty() ? dir : turns.getLast().dir();
        if (turn == last || turn == last.opposite()) {
            return false;
        }
        turns.addLast(new Turn(turn, from));
        return true;
    }

    /**
     * Takes the heading of a move during {@code tick} (which starts from the head position of the
     * tick before): the oldest queued turn once it is due, otherwise straight on.
     */
    Dir nextMove(long tick) {
        Turn next = turns.peekFirst();
        if (next != null && next.from() < tick) {
            turns.removeFirst();
            dir = next.dir();
        }
        return dir;
    }

    /** Moves the head to {@code cell}; the tail follows unless the snake eats. */
    void advance(int cell, boolean grow) {
        body.addFirst(cell);
        if (!grow) {
            body.removeLast();
        }
        maxLength = Math.max(maxLength, body.size());
    }

    /**
     * Takes the snake off the board.
     *
     * @param respawnTick when it comes back, {@code -1} for never
     * @return the cells it occupied, head first
     */
    List<Integer> crash(long respawnTick) {
        List<Integer> cells = List.copyOf(body);
        body.clear();
        turns.clear();
        alive = false;
        crashes++;
        respawnAt = respawnTick;
        power = null;
        return cells;
    }

    /** Removes the snake for good (its player left); not counted as a crash. */
    List<Integer> vanish() {
        List<Integer> cells = List.copyOf(body);
        body.clear();
        turns.clear();
        alive = false;
        respawnAt = -1;
        power = null;
        return cells;
    }

    // ------------------------------------------------------------------ power-ups & points

    /** Activates a power-up (replacing the current one) up to and including tick {@code until}. */
    void grant(PowerType type, long until) {
        power = type;
        powerUntil = until;
    }

    /** Ends the power-up once its last tick is over. */
    void expirePower(long tick) {
        if (power != null && tick >= powerUntil) {
            power = null;
        }
    }

    /** Ate a piece of food: +1 point. */
    void ate() {
        food++;
        points++;
    }

    /** Another snake crashed into this one's body. */
    void credit(int bonus) {
        credits++;
        points += bonus;
    }

    /** End-of-round bonus points. */
    void addPoints(int bonus) {
        points += bonus;
    }
}
