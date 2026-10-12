package com.example.funfridaygame.config;

import com.example.funfridaygame.room.RoomStore;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Security of the STOMP channel.
 *
 * <ul>
 *   <li><b>CONNECT</b>: gives every connection its own random principal. Identity is therefore
 *       bound to the connection; names in payloads are never trusted.
 *   <li><b>SUBSCRIBE</b>: only private queues ({@code /user/queue/**}) and the stream of the room
 *       the connection is seated in.
 *   <li><b>SEND</b>: only application destinations ({@code /app/**}); browsers can't publish to
 *       broker topics directly.
 * </ul>
 *
 * Disallowed frames are dropped silently (an ERROR frame would close the connection).
 */
@Component
public class StompSecurityInterceptor implements ChannelInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(StompSecurityInterceptor.class);
    private static final Pattern STREAM = Pattern.compile("/topic/rooms/([A-Z0-9]+)/stream");

    private final RoomStore store;

    public StompSecurityInterceptor(RoomStore store) {
        this.store = store;
    }

    /**
     * The principal of one WebSocket connection.
     *
     * @param name random, unguessable connection id
     */
    public record ConnectionPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();
        return switch (command) {
            case CONNECT, STOMP -> {
                if (accessor.getUser() == null) {
                    accessor.setUser(new ConnectionPrincipal("c-" + UUID.randomUUID()));
                }
                yield message;
            }
            case SUBSCRIBE ->
                    allowSubscription(destination, accessor.getUser())
                            ? message
                            : drop(command, destination);
            case SEND ->
                    destination != null && destination.startsWith("/app/")
                            ? message
                            : drop(command, destination);
            default -> message;
        };
    }

    private boolean allowSubscription(String destination, Principal user) {
        if (destination == null || user == null) {
            return false;
        }
        if (destination.startsWith("/user/queue/")) {
            return true;
        }
        Matcher stream = STREAM.matcher(destination);
        return stream.matches()
                && store.binding(user.getName())
                        .map(binding -> binding.code().equals(stream.group(1)))
                        .orElse(false);
    }

    private Message<?> drop(StompCommand command, String destination) {
        LOG.debug("Dropped {} to {}", command, destination);
        return null;
    }
}
