package com.example.funfridaygame.runtime;

import java.time.Duration;

/** Schedules one-shot tasks. Implementations decide on threads and locking. */
public interface TimerService {

    /**
     * Runs {@code task} once after {@code delay}.
     *
     * @return a handle to cancel the task
     */
    Cancellable schedule(Duration delay, Runnable task);

    /** A scheduled task that can be cancelled. */
    @FunctionalInterface
    interface Cancellable {
        /** Cancels the task if it hasn't run yet. */
        void cancel();
    }
}
