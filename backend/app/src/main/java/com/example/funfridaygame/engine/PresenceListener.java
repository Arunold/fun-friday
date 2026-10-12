package com.example.funfridaygame.engine;

import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Connects WebSocket lifecycle events and periodic maintenance to the {@link RoomService}. */
@Component
public class PresenceListener {

    private final RoomService rooms;

    public PresenceListener(RoomService rooms) {
        this.rooms = rooms;
    }

    /** A browser tab closed, the network dropped or heart-beats stopped. */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            rooms.disconnected(user.getName());
        }
    }

    /** Closes rooms nobody uses anymore. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void evictStaleRooms() {
        rooms.evictStaleRooms();
    }
}
