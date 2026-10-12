package com.example.funfridaygame.messaging.protocol;

import com.example.funfridaygame.room.ErrorCode;
import com.example.funfridaygame.room.Member;
import org.jspecify.annotations.Nullable;

/** Messages the server sends to a single browser or to a room's stream. */
public final class ServerMessages {

    private ServerMessages() {}

    /**
     * Answer to a request, on {@code /user/queue/reply}.
     *
     * @param requestId the request's correlation id
     * @param ok whether the request succeeded
     * @param error error code when it failed
     * @param message human-readable explanation when it failed
     * @param data result data (e.g. {@link Joined})
     */
    public record Reply(
            @Nullable String requestId,
            boolean ok,
            @Nullable ErrorCode error,
            @Nullable String message,
            @Nullable Object data) {

        public static Reply success(@Nullable String requestId, @Nullable Object data) {
            return new Reply(requestId, true, null, null, data);
        }

        public static Reply failure(@Nullable String requestId, ErrorCode error, String message) {
            return new Reply(requestId, false, error, message, null);
        }
    }

    /**
     * Data of a successful create/join/resume.
     *
     * @param code room code
     * @param memberId your member id
     * @param token secret to resume the seat after a reconnect (keep it private)
     * @param role PLAYER or SPECTATOR
     */
    public record Joined(String code, String memberId, String token, Member.Role role) {}

    /**
     * Answer to a ping.
     *
     * @param clientTime echoed client time
     * @param serverTime server clock in epoch ms
     */
    public record Pong(long clientTime, long serverTime) {}

    /**
     * A notification on {@code /user/queue/events}.
     *
     * @param scope "room" for platform events (kicked, closed, host changed, notice) or "game" for
     *     private game events
     * @param type event type
     * @param message human-readable text, if any
     * @param payload event data, if any
     */
    public record ServerEvent(
            String scope, String type, @Nullable String message, @Nullable Object payload) {

        public static ServerEvent room(String type, String message) {
            return new ServerEvent("room", type, message, null);
        }

        public static ServerEvent game(String type, Object payload) {
            return new ServerEvent("game", type, null, payload);
        }
    }

    /**
     * A public game event on {@code /topic/rooms/{code}/stream} (e.g. drawing strokes).
     *
     * @param seq increasing per room, lets clients detect gaps
     * @param type event type
     * @param payload event data
     */
    public record StreamMessage(long seq, String type, @Nullable Object payload) {}
}
