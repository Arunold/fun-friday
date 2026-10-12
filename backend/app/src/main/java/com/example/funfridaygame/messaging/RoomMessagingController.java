package com.example.funfridaygame.messaging;

import com.example.funfridaygame.engine.RoomService;
import com.example.funfridaygame.messaging.protocol.ClientRequests;
import com.example.funfridaygame.messaging.protocol.ServerMessages.Pong;
import com.example.funfridaygame.messaging.protocol.ServerMessages.Reply;
import com.example.funfridaygame.room.ErrorCode;
import com.example.funfridaygame.room.RoomException;
import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.sdk.InvalidActionException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.InstantSource;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * STOMP endpoints under {@code /app/rooms/*}. Every request is answered on {@code
 * /user/queue/reply} with the request's {@code requestId}; state changes reach everyone as
 * snapshots.
 */
@Controller
@MessageMapping("/rooms")
public class RoomMessagingController {

    private static final Logger LOG = LoggerFactory.getLogger(RoomMessagingController.class);

    private final RoomService rooms;
    private final RoomBroadcaster broadcaster;
    private final JsonMapper json;
    private final InstantSource clock;

    public RoomMessagingController(
            RoomService rooms, RoomBroadcaster broadcaster, JsonMapper json, InstantSource clock) {
        this.rooms = rooms;
        this.broadcaster = broadcaster;
        this.json = json;
        this.clock = clock;
    }

    @MessageMapping("/create")
    public void create(@Payload @Valid ClientRequests.CreateRoom request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () ->
                        rooms.create(
                                principal.getName(),
                                request.name(),
                                request.avatar(),
                                request.gameId(),
                                request.settings()));
    }

    @MessageMapping("/join")
    public void join(@Payload @Valid ClientRequests.JoinRoom request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () ->
                        rooms.join(
                                principal.getName(),
                                request.code(),
                                request.name(),
                                request.avatar(),
                                request.spectator()));
    }

    @MessageMapping("/resume")
    public void resume(@Payload @Valid ClientRequests.Resume request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> rooms.resume(principal.getName(), request.code(), request.token()));
    }

    @MessageMapping("/leave")
    public void leave(@Payload ClientRequests.Simple request, Principal principal) {
        handle(principal, request.requestId(), () -> run(() -> rooms.leave(principal.getName())));
    }

    @MessageMapping("/kick")
    public void kick(@Payload @Valid ClientRequests.Kick request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> run(() -> rooms.kick(principal.getName(), request.memberId())));
    }

    @MessageMapping("/settings")
    public void settings(
            @Payload @Valid ClientRequests.UpdateSettings request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () ->
                        run(
                                () ->
                                        rooms.updateSettings(
                                                principal.getName(),
                                                request.gameId(),
                                                request.settings())));
    }

    @MessageMapping("/ready")
    public void ready(@Payload ClientRequests.SetReady request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> run(() -> rooms.setReady(principal.getName(), request.ready())));
    }

    @MessageMapping("/become-player")
    public void becomePlayer(@Payload ClientRequests.Simple request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> run(() -> rooms.becomePlayer(principal.getName())));
    }

    @MessageMapping("/start")
    public void start(@Payload ClientRequests.Simple request, Principal principal) {
        handle(principal, request.requestId(), () -> run(() -> rooms.start(principal.getName())));
    }

    @MessageMapping("/play-again")
    public void playAgain(@Payload ClientRequests.Simple request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> run(() -> rooms.playAgain(principal.getName())));
    }

    @MessageMapping("/end")
    public void end(@Payload ClientRequests.Simple request, Principal principal) {
        handle(principal, request.requestId(), () -> run(() -> rooms.end(principal.getName())));
    }

    @MessageMapping("/action")
    public void action(@Payload @Valid ClientRequests.GameAction request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> run(() -> rooms.act(principal.getName(), request.type(), request.payload())));
    }

    @MessageMapping("/ping")
    public void ping(@Payload ClientRequests.Ping request, Principal principal) {
        handle(
                principal,
                request.requestId(),
                () -> new Pong(request.clientTime(), clock.millis()));
    }

    /** Payload validation failures (e.g. a missing name). */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void invalidPayload(
            MethodArgumentNotValidException e, Message<?> message, Principal principal) {
        String detail =
                e.getBindingResult() == null || e.getBindingResult().getFieldError() == null
                        ? "Invalid request"
                        : e.getBindingResult().getFieldError().getField()
                                + " "
                                + e.getBindingResult().getFieldError().getDefaultMessage();
        reply(principal, Reply.failure(requestIdOf(message), ErrorCode.BAD_REQUEST, detail));
    }

    /** Anything else that escaped (e.g. unreadable JSON). */
    @MessageExceptionHandler(Exception.class)
    public void unexpected(Exception e, Message<?> message, Principal principal) {
        LOG.warn("Unhandled STOMP error: {}", e.toString());
        reply(
                principal,
                Reply.failure(requestIdOf(message), ErrorCode.BAD_REQUEST, "Invalid request"));
    }

    // ------------------------------------------------------------------ helpers

    private void handle(Principal principal, String requestId, Supplier<Object> action) {
        Reply reply;
        try {
            reply = Reply.success(requestId, action.get());
        } catch (RoomException e) {
            reply = Reply.failure(requestId, e.code(), e.getMessage());
        } catch (InvalidActionException e) {
            reply = Reply.failure(requestId, ErrorCode.INVALID_ACTION, e.getMessage());
        } catch (InvalidSettingsException e) {
            reply = Reply.failure(requestId, ErrorCode.INVALID_SETTINGS, e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Request {} failed", requestId, e);
            reply = Reply.failure(requestId, ErrorCode.INTERNAL, "Something went wrong.");
        }
        if (reply.ok() && requestId == null) {
            // Fire-and-forget request (e.g. a burst of drawing strokes): only failures are
            // reported.
            return;
        }
        reply(principal, reply);
    }

    private static Object run(Runnable action) {
        action.run();
        return null;
    }

    private void reply(Principal principal, Reply reply) {
        if (principal != null) {
            broadcaster.reply(principal.getName(), reply);
        }
    }

    private String requestIdOf(Message<?> message) {
        try {
            if (message.getPayload() instanceof byte[] bytes) {
                JsonNode node = json.readTree(bytes);
                JsonNode id = node.get("requestId");
                return id == null || id.isNull() ? null : id.asString();
            }
        } catch (RuntimeException ignored) {
            // not JSON — no request id to echo
        }
        return null;
    }
}
