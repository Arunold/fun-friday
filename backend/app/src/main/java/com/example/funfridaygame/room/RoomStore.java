package com.example.funfridaygame.room;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * All live rooms plus the binding of WebSocket connections (principals) to room members.
 *
 * <p>Room codes use an alphabet without look-alike characters (no 0/O, 1/I) and are checked for
 * collisions.
 */
@Component
public class RoomStore {

    /** Unambiguous characters for codes that are read aloud on calls. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    static final int CODE_LENGTH = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();

    /**
     * Where a connection is seated.
     *
     * @param code room code
     * @param memberId member id in that room
     */
    public record Binding(String code, String memberId) {}

    /** Creates a room with a fresh, unused code. */
    public Room create(long now, int maxRooms) {
        if (rooms.size() >= maxRooms) {
            throw new RoomException(
                    ErrorCode.TOO_MANY_ROOMS, "The server is full right now. Try again later.");
        }
        for (int attempt = 0; attempt < 50; attempt++) {
            Room room = new Room(randomCode(), now);
            if (rooms.putIfAbsent(room.code(), room) == null) {
                return room;
            }
        }
        throw new IllegalStateException("Could not find a free room code");
    }

    /** A room by code (case-insensitive). */
    public Optional<Room> find(String code) {
        return code == null
                ? Optional.empty()
                : Optional.ofNullable(rooms.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public Collection<Room> all() {
        return List.copyOf(rooms.values());
    }

    public int size() {
        return rooms.size();
    }

    /** Forgets a room and every connection bound to it. */
    public void remove(Room room) {
        rooms.remove(room.code(), room);
        bindings.values().removeIf(b -> b.code().equals(room.code()));
    }

    public void bind(String principal, Room room, Member member) {
        bindings.put(principal, new Binding(room.code(), member.id()));
    }

    public void unbind(String principal) {
        if (principal != null) {
            bindings.remove(principal);
        }
    }

    public Optional<Binding> binding(String principal) {
        return Optional.ofNullable(principal == null ? null : bindings.get(principal));
    }

    private static String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
