package com.example.funfridaygame.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings ({@code app.*} in application.yml).
 *
 * @param dataDir where the embedded H2 database lives
 * @param cors browser origins allowed to call the REST API (comma-separated)
 * @param websocketOrigins browser origins allowed to open the WebSocket (comma-separated)
 * @param timing platform timings
 * @param rooms room limits and eviction rules
 * @param rateLimit per-player action rate limit
 * @param e2e test-only switches
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("./data") String dataDir,
        @DefaultValue("http://localhost:4200") String cors,
        @DefaultValue("http://localhost:4200") String websocketOrigins,
        @DefaultValue Timing timing,
        @DefaultValue Rooms rooms,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue E2e e2e) {

    /**
     * Platform timings.
     *
     * @param startCountdown countdown between "Start" and the first turn
     * @param reveal how long each turn's result stays on screen
     * @param presenceGrace how long a disconnected player keeps their seat (refresh, Wi-Fi blip)
     * @param broadcastCoalesce how long action-driven updates may be batched
     */
    public record Timing(
            @DefaultValue("3s") Duration startCountdown,
            @DefaultValue("5s") Duration reveal,
            @DefaultValue("30s") Duration presenceGrace,
            @DefaultValue("50ms") Duration broadcastCoalesce) {}

    /**
     * Room limits and eviction rules.
     *
     * @param maxRooms rooms that may exist at the same time
     * @param idleTimeout close a room nobody is connected to after this long
     * @param inactiveTimeout close a room without any activity after this long
     * @param maxAge close any room after this long
     */
    public record Rooms(
            @DefaultValue("500") int maxRooms,
            @DefaultValue("10m") Duration idleTimeout,
            @DefaultValue("2h") Duration inactiveTimeout,
            @DefaultValue("12h") Duration maxAge) {}

    /**
     * Token bucket per player.
     *
     * @param capacity burst size
     * @param refillPerSecond sustained actions per second
     */
    public record RateLimit(
            @DefaultValue("40") int capacity, @DefaultValue("20") int refillPerSecond) {}

    /**
     * Test-only switches.
     *
     * @param enabled exposes {@code /api/e2e/**} (secret game state) — never in production
     */
    public record E2e(@DefaultValue("false") boolean enabled) {}

    /** Allowed REST origins, trimmed. */
    public List<String> corsOrigins() {
        return split(cors);
    }

    /** Allowed WebSocket origins, trimmed. */
    public List<String> websocketOriginList() {
        return split(websocketOrigins);
    }

    private static List<String> split(String value) {
        return value == null
                ? List.of()
                : Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
    }
}
