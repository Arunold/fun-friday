package com.example.funfridaygame.room;

import com.example.funfridaygame.runtime.GameResult;
import com.example.funfridaygame.runtime.GameRun;
import com.example.funfridaygame.runtime.TimerService;
import com.example.funfridaygame.sdk.GameSettings;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A room: members, the selected game and settings, the current game run and the room's Friday Cup.
 * Every field is guarded by {@link #lock()}; use {@link #locked(Supplier)}.
 */
public final class Room {

    /** Distinct player colours, assigned in join order. */
    static final List<String> COLORS =
            List.of(
                    "#1ed760", "#ff6b6b", "#4dabf7", "#ffd43b", "#cc5de8", "#ff922b", "#20c997",
                    "#f06595", "#94d82d", "#74c0fc", "#e599f7", "#ffa94d", "#63e6be", "#ff8787",
                    "#a5d8ff", "#ffe066");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String code;
    private final String instanceId = UUID.randomUUID().toString();
    private final long createdAt;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Map<String, CupEntry> cup = new LinkedHashMap<>();

    private String hostId;
    private RoomPhase phase = RoomPhase.LOBBY;
    private String gameId;
    private GameSettings settings;
    private GameRun run;
    private long startingStartedAt;
    private long startingEndsAt;
    private long lastActivity;
    private long streamSequence;
    private boolean closed;
    private boolean broadcastPending;
    private TimerService.Cancellable startTimer;

    Room(String code, long now) {
        this.code = code;
        this.createdAt = now;
        this.lastActivity = now;
    }

    // ------------------------------------------------------------------ locking

    public ReentrantLock lock() {
        return lock;
    }

    /** Runs {@code action} while holding the room lock. */
    public <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /** Runs {@code action} while holding the room lock. */
    public void locked(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ members

    /** Adds a member with a fresh id, resume token and colour. */
    public Member addMember(
            String name, String avatar, Member.Role role, long now, RateLimiter limiter) {
        String id = "m" + Long.toString(Math.abs(RANDOM.nextLong()), 36);
        Member member = new Member(id, newToken(), name, avatar, nextColor(), role, now, limiter);
        members.put(id, member);
        return member;
    }

    public void removeMember(String memberId) {
        members.remove(memberId);
    }

    public Optional<Member> member(String memberId) {
        return Optional.ofNullable(memberId == null ? null : members.get(memberId));
    }

    public Optional<Member> memberByToken(String token) {
        return members.values().stream().filter(m -> m.token().equals(token)).findFirst();
    }

    /** Case-insensitive lookup by display name. */
    public Optional<Member> memberByName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return members.values().stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).equals(key))
                .findFirst();
    }

    public Collection<Member> members() {
        return members.values();
    }

    /** Players in join order. */
    public List<Member> players() {
        return members.values().stream().filter(Member::isPlayer).toList();
    }

    /** Spectators in join order. */
    public List<Member> spectators() {
        return members.values().stream().filter(m -> !m.isPlayer()).toList();
    }

    public boolean hasOnlineMembers() {
        return members.values().stream().anyMatch(Member::online);
    }

    private String nextColor() {
        Set<String> used = new HashSet<>();
        members.values().forEach(m -> used.add(m.color()));
        return COLORS.stream()
                .filter(c -> !used.contains(c))
                .findFirst()
                .orElse(COLORS.get(members.size() % COLORS.size()));
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ------------------------------------------------------------------ friday cup

    /**
     * Adds a finished game to the room's Friday Cup: 10 points for 1st, 6 for 2nd, 4 for 3rd and 2
     * for taking part; ties share the better placement.
     */
    public void recordCup(GameResult result) {
        for (GameResult.Standing standing : result.standings()) {
            String key = standing.player().name().toLowerCase(Locale.ROOT);
            CupEntry entry =
                    cup.computeIfAbsent(
                            key,
                            k ->
                                    new CupEntry(
                                            standing.player().name(), standing.player().avatar()));
            entry.points += cupPoints(standing.rank());
            entry.games++;
            if (standing.rank() == 1) {
                entry.wins++;
            }
            entry.avatar = standing.player().avatar();
        }
    }

    /** Cup points for a placement. */
    public static int cupPoints(int rank) {
        return switch (rank) {
            case 1 -> 10;
            case 2 -> 6;
            case 3 -> 4;
            default -> 2;
        };
    }

    /** Friday Cup standings, best first. */
    public List<CupEntry> cupStandings() {
        List<CupEntry> list = new ArrayList<>(cup.values());
        list.sort(
                (a, b) ->
                        a.points != b.points
                                ? Integer.compare(b.points, a.points)
                                : Integer.compare(b.wins, a.wins));
        return list;
    }

    /** One line of the Friday Cup. */
    public static final class CupEntry {
        private final String name;
        private String avatar;
        private int points;
        private int games;
        private int wins;

        CupEntry(String name, String avatar) {
            this.name = name;
            this.avatar = avatar;
        }

        public String name() {
            return name;
        }

        public String avatar() {
            return avatar;
        }

        public int points() {
            return points;
        }

        public int games() {
            return games;
        }

        public int wins() {
            return wins;
        }
    }

    // ------------------------------------------------------------------ state

    public String code() {
        return code;
    }

    /** Unique per room instance (codes are reused after a room closes). */
    public String instanceId() {
        return instanceId;
    }

    public long createdAt() {
        return createdAt;
    }

    public String hostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public boolean isHost(Member member) {
        return member != null && member.id().equals(hostId);
    }

    public RoomPhase phase() {
        return phase;
    }

    public void setPhase(RoomPhase phase) {
        this.phase = phase;
    }

    public String gameId() {
        return gameId;
    }

    public GameSettings settings() {
        return settings;
    }

    public void selectGame(String id, GameSettings values) {
        this.gameId = id;
        this.settings = values;
    }

    /** The current game run (also kept while the podium is shown), or {@code null}. */
    public GameRun run() {
        return run;
    }

    public void setRun(GameRun run) {
        this.run = run;
    }

    public long startingStartedAt() {
        return startingStartedAt;
    }

    public long startingEndsAt() {
        return startingEndsAt;
    }

    /** The pre-game countdown runs from {@code startedAt} to {@code endsAt} (epoch ms). */
    public void setStartingWindow(long startedAt, long endsAt) {
        this.startingStartedAt = startedAt;
        this.startingEndsAt = endsAt;
    }

    public long lastActivity() {
        return lastActivity;
    }

    public void touch(long now) {
        lastActivity = now;
    }

    public long nextStreamSequence() {
        return ++streamSequence;
    }

    public boolean closed() {
        return closed;
    }

    public void markClosed() {
        closed = true;
    }

    public boolean broadcastPending() {
        return broadcastPending;
    }

    public void setBroadcastPending(boolean pending) {
        this.broadcastPending = pending;
    }

    public TimerService.Cancellable startTimer() {
        return startTimer;
    }

    public void setStartTimer(TimerService.Cancellable timer) {
        this.startTimer = timer;
    }
}
