package com.example.funfridaygame.engine;

import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.runtime.TimerService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * The one scheduler for all room timers. Every task runs while holding its room's lock and is
 * skipped once the room is closed; failures are logged instead of disappearing silently.
 */
@Component
public class RoomScheduler implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(RoomScheduler.class);

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(
                    4, Thread.ofPlatform().name("room-timer-", 0).daemon().factory());

    /** Runs {@code task} for {@code room} after {@code delay}. */
    public TimerService.Cancellable schedule(Room room, Duration delay, Runnable task) {
        ScheduledFuture<?> future =
                executor.schedule(
                        () -> runLocked(room, task),
                        Math.max(0, delay.toMillis()),
                        TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    /** A {@link TimerService} whose tasks run under {@code room}'s lock. */
    public TimerService timersFor(Room room) {
        return (delay, task) -> schedule(room, delay, task);
    }

    /** Runs {@code task} under the room lock, with the room code in the logging context. */
    public void runLocked(Room room, Runnable task) {
        room.lock().lock();
        try {
            if (room.closed()) {
                return;
            }
            MDC.put("room", room.code());
            task.run();
        } catch (RuntimeException e) {
            LOG.error("Timer task failed in room {}", room.code(), e);
        } finally {
            MDC.remove("room");
            room.lock().unlock();
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
