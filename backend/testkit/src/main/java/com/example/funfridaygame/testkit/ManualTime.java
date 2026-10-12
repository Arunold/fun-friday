package com.example.funfridaygame.testkit;

import com.example.funfridaygame.runtime.TimerService;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.PriorityQueue;

/** A virtual clock plus a timer queue that only moves when a test says so. */
final class ManualTime implements InstantSource, TimerService {

    /** Every harness starts at the same instant so tests are reproducible. */
    static final long START = Instant.parse("2026-01-02T15:00:00Z").toEpochMilli();

    private final PriorityQueue<Entry> queue =
            new PriorityQueue<>(Comparator.comparingLong(Entry::due).thenComparingLong(Entry::seq));
    private long now = START;
    private long sequence;

    @Override
    public long millis() {
        return now;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(now);
    }

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
        Entry entry = new Entry(now + Math.max(0, delay.toMillis()), sequence++, task);
        queue.add(entry);
        return () -> entry.cancelled = true;
    }

    /** Moves time forward, running every timer that becomes due, in order. */
    void advanceTo(long target) {
        while (true) {
            Entry next = queue.peek();
            if (next == null || next.due > target) {
                break;
            }
            queue.poll();
            if (next.cancelled) {
                continue;
            }
            now = Math.max(now, next.due);
            next.task.run();
        }
        now = Math.max(now, target);
    }

    /** Runs the next pending timer; returns {@code false} when there is none. */
    boolean runNext() {
        while (!queue.isEmpty()) {
            Entry next = queue.poll();
            if (!next.cancelled) {
                now = Math.max(now, next.due);
                next.task.run();
                return true;
            }
        }
        return false;
    }

    private static final class Entry {
        private final long due;
        private final long seq;
        private final Runnable task;
        private boolean cancelled;

        private Entry(long due, long seq, Runnable task) {
            this.due = due;
            this.seq = seq;
            this.task = task;
        }

        long due() {
            return due;
        }

        long seq() {
            return seq;
        }
    }
}
