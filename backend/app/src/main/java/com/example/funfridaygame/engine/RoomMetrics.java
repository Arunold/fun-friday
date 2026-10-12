package com.example.funfridaygame.engine;

import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.room.RoomStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Micrometer metrics: live rooms, connected members, games started and finished per game. */
@Component
public class RoomMetrics {

    private final MeterRegistry registry;

    public RoomMetrics(MeterRegistry registry, RoomStore store) {
        this.registry = registry;
        Gauge.builder("funfriday.rooms.active", store, RoomStore::size)
                .description("Rooms that currently exist")
                .register(registry);
        Gauge.builder("funfriday.members.online", store, RoomMetrics::onlineMembers)
                .description("Members connected right now")
                .register(registry);
    }

    private static double onlineMembers(RoomStore store) {
        long online = 0;
        for (Room room : store.all()) {
            online += room.locked(() -> room.members().stream().filter(Member::online).count());
        }
        return online;
    }

    public void roomCreated() {
        registry.counter("funfriday.rooms.created").increment();
    }

    public void gameStarted(String gameId) {
        registry.counter("funfriday.games.started", "game", gameId).increment();
    }

    public void gameFinished(String gameId) {
        registry.counter("funfriday.games.finished", "game", gameId).increment();
    }
}
