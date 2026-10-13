package com.example.funfridaygame.games.snakearena;

import com.example.funfridaygame.games.snakearena.SnakeArenaView.FeedEntry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The last few crashes of a round ("Ana ⚔️ Dev"), shown next to the arena. */
final class KillFeed {

    /** Entries kept. */
    static final int SIZE = 5;

    /** What happened. */
    enum Kind {
        /** {@code b} ran into {@code a}'s body; {@code a} earned the +5. */
        BODY,
        /** {@code a} and {@code b} collided head-on (or swapped heads). */
        HEAD,
        /** {@code a} hit a wall. */
        WALL,
        /** {@code a} ran into its own body. */
        SELF,
        /** {@code a} left the game; their snake turned into food. */
        LEFT
    }

    private final Deque<FeedEntry> entries = new ArrayDeque<>();
    private int nextId = 1;

    /** Adds an entry, dropping the oldest beyond {@value #SIZE}. */
    void add(Kind kind, String a, String b) {
        entries.addLast(new FeedEntry(nextId++, kind.name(), a, b));
        while (entries.size() > SIZE) {
            entries.removeFirst();
        }
    }

    /** Oldest first. */
    List<FeedEntry> entries() {
        return List.copyOf(entries);
    }
}
