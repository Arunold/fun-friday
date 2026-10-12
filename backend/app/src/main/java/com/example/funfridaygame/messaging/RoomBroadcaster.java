package com.example.funfridaygame.messaging;

import com.example.funfridaygame.config.AppProperties;
import com.example.funfridaygame.engine.RoomScheduler;
import com.example.funfridaygame.messaging.protocol.ServerMessages.Reply;
import com.example.funfridaygame.messaging.protocol.ServerMessages.ServerEvent;
import com.example.funfridaygame.messaging.protocol.ServerMessages.StreamMessage;
import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.Room;
import java.time.InstantSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends state to browsers.
 *
 * <ul>
 *   <li>Snapshots go to each member's private queue ({@code /user/queue/room}), so every viewer
 *       gets only what they may see.
 *   <li>Action-driven updates are coalesced for a few milliseconds so a burst of keystrokes or
 *       moves produces one update per member.
 *   <li>High-frequency public game events go to the room topic ({@code
 *       /topic/rooms/{code}/stream}).
 * </ul>
 *
 * <p>Snapshot methods must be called while holding the room lock.
 */
@Component
public class RoomBroadcaster {

    public static final String ROOM_QUEUE = "/queue/room";
    public static final String REPLY_QUEUE = "/queue/reply";
    public static final String EVENT_QUEUE = "/queue/events";

    private final SimpMessagingTemplate messaging;
    private final SnapshotFactory snapshots;
    private final RoomScheduler scheduler;
    private final AppProperties properties;
    private final InstantSource clock;

    public RoomBroadcaster(
            SimpMessagingTemplate messaging,
            SnapshotFactory snapshots,
            RoomScheduler scheduler,
            AppProperties properties,
            InstantSource clock) {
        this.messaging = messaging;
        this.snapshots = snapshots;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sends a fresh snapshot to every connected member.
     *
     * @param urgent send now; otherwise coalesce with other updates in the next few milliseconds
     */
    public void snapshot(Room room, boolean urgent) {
        if (room.closed()) {
            return;
        }
        if (urgent) {
            sendAll(room);
            return;
        }
        if (!room.broadcastPending()) {
            room.setBroadcastPending(true);
            scheduler.schedule(room, properties.timing().broadcastCoalesce(), () -> sendAll(room));
        }
    }

    private void sendAll(Room room) {
        room.setBroadcastPending(false);
        long now = clock.millis();
        for (Member member : room.members()) {
            if (member.online() && member.principal() != null) {
                messaging.convertAndSendToUser(
                        member.principal(), ROOM_QUEUE, snapshots.build(room, member, now));
            }
        }
    }

    /** Sends a private event to a member (if connected). */
    public void event(Member member, ServerEvent event) {
        if (member.principal() != null) {
            messaging.convertAndSendToUser(member.principal(), EVENT_QUEUE, event);
        }
    }

    /** Sends a private event to a connection. */
    public void event(String principal, ServerEvent event) {
        messaging.convertAndSendToUser(principal, EVENT_QUEUE, event);
    }

    /** Answers a request. */
    public void reply(String principal, Reply reply) {
        messaging.convertAndSendToUser(principal, REPLY_QUEUE, reply);
    }

    /** Publishes a public game event to the room's stream topic. */
    public void stream(Room room, String type, Object payload) {
        messaging.convertAndSend(
                "/topic/rooms/" + room.code() + "/stream",
                new StreamMessage(room.nextStreamSequence(), type, payload));
    }
}
