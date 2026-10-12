package com.example.funfridaygame.messaging.protocol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Messages browsers send to {@code /app/rooms/*}. Every request may carry a {@code requestId}; the
 * server answers on {@code /user/queue/reply} with the same id. The sender's identity comes from
 * the WebSocket session, never from the payload.
 */
public final class ClientRequests {

    private ClientRequests() {}

    /**
     * {@code /app/rooms/create} — create a room and join it as host.
     *
     * @param requestId correlation id
     * @param name display name
     * @param avatar emoji
     * @param gameId selected game
     * @param settings partial settings; missing values use defaults
     */
    public record CreateRoom(
            String requestId,
            @NotBlank @Size(max = 40) String name,
            @Size(max = 32) String avatar,
            @NotBlank String gameId,
            Map<String, Object> settings) {}

    /**
     * {@code /app/rooms/join} — join a room as player (or spectator when the game is running).
     *
     * @param requestId correlation id
     * @param code room code
     * @param name display name
     * @param avatar emoji
     * @param spectator join as spectator even if a seat is free
     */
    public record JoinRoom(
            String requestId,
            @NotBlank @Size(max = 10) String code,
            @NotBlank @Size(max = 40) String name,
            @Size(max = 32) String avatar,
            boolean spectator) {}

    /**
     * {@code /app/rooms/resume} — reclaim a seat after a refresh or reconnect.
     *
     * @param requestId correlation id
     * @param code room code
     * @param token secret received when joining
     */
    public record Resume(
            String requestId, @NotBlank @Size(max = 10) String code, @NotBlank String token) {}

    /**
     * Requests without parameters: {@code leave}, {@code start}, {@code play-again}, {@code end},
     * {@code become-player}.
     *
     * @param requestId correlation id
     */
    public record Simple(String requestId) {}

    /**
     * {@code /app/rooms/kick} — host removes a member.
     *
     * @param requestId correlation id
     * @param memberId member to remove
     */
    public record Kick(String requestId, @NotBlank String memberId) {}

    /**
     * {@code /app/rooms/settings} — host picks a game and settings (lobby or podium).
     *
     * @param requestId correlation id
     * @param gameId game to play next
     * @param settings partial settings; missing values use defaults
     */
    public record UpdateSettings(
            String requestId, @NotBlank String gameId, Map<String, Object> settings) {}

    /**
     * {@code /app/rooms/ready} — toggle "ready" in the lobby.
     *
     * @param requestId correlation id
     * @param ready new state
     */
    public record SetReady(String requestId, boolean ready) {}

    /**
     * {@code /app/rooms/action} — a game move, handled by the running game.
     *
     * @param requestId correlation id (optional; frequent actions may omit it)
     * @param type action name defined by the game
     * @param payload action data defined by the game
     */
    public record GameAction(
            String requestId, @NotBlank @Size(max = 40) String type, Object payload) {}

    /**
     * {@code /app/rooms/ping} — clock sync; answered with {@link ServerMessages.Pong}.
     *
     * @param requestId correlation id
     * @param clientTime the client's clock in epoch ms
     */
    public record Ping(String requestId, long clientTime) {}
}
