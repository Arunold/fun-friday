package com.example.funfridaygame.room;

/** Token bucket: {@code capacity} actions burst, refilled at {@code refillPerSecond}. */
public final class RateLimiter {

    private final int capacity;
    private final double refillPerMilli;
    private double tokens;
    private long lastRefill;

    public RateLimiter(int capacity, int refillPerSecond, long now) {
        this.capacity = capacity;
        this.refillPerMilli = refillPerSecond / 1000.0;
        this.tokens = capacity;
        this.lastRefill = now;
    }

    /** Takes one token if available. Callers synchronize (the room lock). */
    public boolean tryAcquire(long now) {
        if (now > lastRefill) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMilli);
            lastRefill = now;
        }
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}
