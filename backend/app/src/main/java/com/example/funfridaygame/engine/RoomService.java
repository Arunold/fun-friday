package com.example.funfridaygame.engine;

import com.example.funfridaygame.catalog.GameCatalog;
import com.example.funfridaygame.config.AppProperties;
import com.example.funfridaygame.history.HistoryService;
import com.example.funfridaygame.messaging.RoomBroadcaster;
import com.example.funfridaygame.messaging.protocol.ServerMessages.Joined;
import com.example.funfridaygame.messaging.protocol.ServerMessages.ServerEvent;
import com.example.funfridaygame.room.ErrorCode;
import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.NameRules;
import com.example.funfridaygame.room.RateLimiter;
import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.room.RoomException;
import com.example.funfridaygame.room.RoomPhase;
import com.example.funfridaygame.room.RoomStore;
import com.example.funfridaygame.runtime.GameResult;
import com.example.funfridaygame.runtime.GameRun;
import com.example.funfridaygame.runtime.GameRunConfig;
import com.example.funfridaygame.runtime.GameRunListener;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameSettings;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Every room command, presence change and game lifecycle step. Callers are identified by their
 * WebSocket principal; the principal is bound to a member when they create, join or resume.
 *
 * <p>All state changes happen while holding the room's lock, so commands, timers and disconnects of
 * one room never interleave.
 */
@Service
public class RoomService {

    private static final Logger LOG = LoggerFactory.getLogger(RoomService.class);

    private final RoomStore store;
    private final GameCatalog catalog;
    private final RoomBroadcaster broadcaster;
    private final RoomScheduler scheduler;
    private final SpringActionConverter converter;
    private final HistoryService history;
    private final RoomMetrics metrics;
    private final AppProperties properties;
    private final InstantSource clock;

    public RoomService(
            RoomStore store,
            GameCatalog catalog,
            RoomBroadcaster broadcaster,
            RoomScheduler scheduler,
            SpringActionConverter converter,
            HistoryService history,
            RoomMetrics metrics,
            AppProperties properties,
            InstantSource clock) {
        this.store = store;
        this.catalog = catalog;
        this.broadcaster = broadcaster;
        this.scheduler = scheduler;
        this.converter = converter;
        this.history = history;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    // ================================================================== joining

    /** Creates a room for {@code gameId} and seats the caller as host. */
    public Joined create(
            String principal, String rawName, String rawAvatar, String gameId, Map<String, ?> raw) {
        String name = NameRules.normalize(rawName);
        String avatar = NameRules.avatar(rawAvatar);
        GameSettings settings = catalog.resolve(gameId, raw);
        leaveCurrentRoom(principal);
        Room room = store.create(now(), properties.rooms().maxRooms());
        return room.locked(
                () -> {
                    room.selectGame(gameId, settings);
                    Member host =
                            room.addMember(name, avatar, Member.Role.PLAYER, now(), limiter());
                    room.setHostId(host.id());
                    attach(room, host, principal);
                    metrics.roomCreated();
                    LOG.info("Room {} created by {} ({})", room.code(), name, gameId);
                    broadcaster.snapshot(room, true);
                    return joined(room, host);
                });
    }

    /**
     * Joins a room. Players join as spectators while a game is running (they play from the next
     * game on), when the room is full, or when they ask to watch.
     */
    public Joined join(
            String principal, String code, String rawName, String rawAvatar, boolean spectator) {
        String name = NameRules.normalize(rawName);
        String avatar = NameRules.avatar(rawAvatar);
        Room room = store.find(code).orElseThrow(() -> notFound(code));
        Optional<RoomStore.Binding> current = store.binding(principal);
        if (current.isPresent() && current.get().code().equals(room.code())) {
            return room.locked(
                    () -> joined(room, room.member(current.get().memberId()).orElseThrow()));
        }
        leaveCurrentRoom(principal);
        return room.locked(
                () -> {
                    if (room.closed()) {
                        throw notFound(code);
                    }
                    if (room.memberByName(name).isPresent()) {
                        throw new RoomException(
                                ErrorCode.NAME_TAKEN,
                                "Someone in this room is already called " + name + ".");
                    }
                    int maxPlayers = catalog.require(room.gameId()).info().maxPlayers();
                    boolean running =
                            room.phase() != RoomPhase.LOBBY && room.phase() != RoomPhase.FINISHED;
                    boolean seatFree = room.players().size() < maxPlayers;
                    Member.Role role =
                            spectator || running || !seatFree
                                    ? Member.Role.SPECTATOR
                                    : Member.Role.PLAYER;
                    Member member = room.addMember(name, avatar, role, now(), limiter());
                    member.setWantsToPlay(!spectator);
                    attach(room, member, principal);
                    LOG.info("{} joined room {} as {}", name, room.code(), role);
                    broadcaster.snapshot(room, true);
                    return joined(room, member);
                });
    }

    /** Re-binds a returning browser (refresh, reconnect) to its seat using the secret token. */
    public Joined resume(String principal, String code, String token) {
        Room room =
                store.find(code)
                        .orElseThrow(
                                () ->
                                        new RoomException(
                                                ErrorCode.SESSION_EXPIRED,
                                                "This room doesn't exist anymore."));
        Optional<RoomStore.Binding> current = store.binding(principal);
        if (current.isPresent() && !current.get().code().equals(room.code())) {
            leaveCurrentRoom(principal);
        }
        return room.locked(
                () -> {
                    if (room.closed()) {
                        throw new RoomException(
                                ErrorCode.SESSION_EXPIRED, "This room doesn't exist anymore.");
                    }
                    Member member =
                            room.memberByToken(token)
                                    .orElseThrow(
                                            () ->
                                                    new RoomException(
                                                            ErrorCode.SESSION_EXPIRED,
                                                            "Your seat in this room has expired."));
                    String previous = member.principal();
                    if (previous != null && !previous.equals(principal)) {
                        store.unbind(previous);
                        broadcaster.event(
                                previous,
                                ServerEvent.room(
                                        "REPLACED", "This room was opened in another tab."));
                    }
                    attach(room, member, principal);
                    broadcaster.snapshot(room, true);
                    return joined(room, member);
                });
    }

    private void attach(Room room, Member member, String principal) {
        member.setPrincipal(principal);
        member.setOnline(true);
        member.nextPresenceEpoch();
        store.bind(principal, room, member);
        if (room.run() != null) {
            room.run().setConnected(member.id(), true);
        }
        room.touch(now());
    }

    private Joined joined(Room room, Member member) {
        return new Joined(room.code(), member.id(), member.token(), member.role());
    }

    // ================================================================== leaving

    /** The caller leaves their room for good. */
    public void leave(String principal) {
        withMember(principal, (room, member) -> removeMember(room, member, "left"));
        store.unbind(principal);
    }

    /** The host removes someone. */
    public void kick(String principal, String memberId) {
        withMember(
                principal,
                (room, me) -> {
                    requireHost(room, me);
                    Member target =
                            room.member(memberId)
                                    .orElseThrow(
                                            () ->
                                                    new RoomException(
                                                            ErrorCode.NOT_IN_ROOM,
                                                            "That person already left."));
                    if (target == me) {
                        throw new RoomException(
                                ErrorCode.BAD_REQUEST, "Use \"Leave\" to leave the room.");
                    }
                    broadcaster.event(
                            target,
                            ServerEvent.room("KICKED", "The host removed you from the room."));
                    removeMember(room, target, "was removed by the host");
                });
    }

    /** The host closes the room for everyone. */
    public void end(String principal) {
        withMember(
                principal,
                (room, me) -> {
                    requireHost(room, me);
                    closeRoom(room, "The host ended the game.");
                });
    }

    private void leaveCurrentRoom(String principal) {
        store.binding(principal)
                .ifPresent(
                        binding -> {
                            store.find(binding.code())
                                    .ifPresent(
                                            room ->
                                                    room.locked(
                                                            () ->
                                                                    room.member(binding.memberId())
                                                                            .ifPresent(
                                                                                    m ->
                                                                                            removeMember(
                                                                                                    room,
                                                                                                    m,
                                                                                                    "left"))));
                            store.unbind(principal);
                        });
    }

    private void removeMember(Room room, Member member, String reason) {
        room.removeMember(member.id());
        store.unbind(member.principal());
        if (room.run() != null) {
            room.run().removePlayer(member.id());
        }
        LOG.info("{} {} (room {})", member.name(), reason, room.code());
        if (room.players().isEmpty()) {
            closeRoom(room, "Everyone left the room.");
            return;
        }
        if (member.id().equals(room.hostId())) {
            migrateHost(room);
        }
        broadcaster.snapshot(room, true);
    }

    private void migrateHost(Room room) {
        List<Member> players = room.players();
        Member next =
                players.stream().filter(Member::online).findFirst().orElse(players.getFirst());
        room.setHostId(next.id());
        for (Member member : room.members()) {
            broadcaster.event(
                    member, ServerEvent.room("HOST_CHANGED", next.name() + " is now the host."));
        }
    }

    private void closeRoom(Room room, String reason) {
        if (room.closed()) {
            return;
        }
        room.markClosed();
        if (room.run() != null) {
            room.run().abort();
        }
        if (room.startTimer() != null) {
            room.startTimer().cancel();
        }
        for (Member member : room.members()) {
            broadcaster.event(member, ServerEvent.room("ROOM_CLOSED", reason));
        }
        store.remove(room);
        LOG.info("Room {} closed: {}", room.code(), reason);
    }

    // ================================================================== lobby

    /** The host picks the next game and its settings (in the lobby or on the podium). */
    public void updateSettings(String principal, String gameId, Map<String, ?> raw) {
        withMember(
                principal,
                (room, me) -> {
                    requireHost(room, me);
                    if (room.phase() != RoomPhase.LOBBY && room.phase() != RoomPhase.FINISHED) {
                        throw new RoomException(
                                ErrorCode.WRONG_PHASE, "Settings can only change between games.");
                    }
                    GameDefinition definition = catalog.require(gameId);
                    GameSettings settings = catalog.resolve(gameId, raw);
                    if (room.players().size() > definition.info().maxPlayers()) {
                        throw new RoomException(
                                ErrorCode.TOO_MANY_PLAYERS,
                                definition.info().name()
                                        + " allows at most "
                                        + definition.info().maxPlayers()
                                        + " players.");
                    }
                    room.selectGame(gameId, settings);
                    if (room.phase() == RoomPhase.FINISHED) {
                        backToLobby(room);
                    }
                    room.members().forEach(m -> m.setReady(false));
                    broadcaster.snapshot(room, true);
                });
    }

    /** A player toggles "ready". */
    public void setReady(String principal, boolean ready) {
        withMember(
                principal,
                (room, me) -> {
                    if (room.phase() != RoomPhase.LOBBY || !me.isPlayer()) {
                        throw new RoomException(ErrorCode.WRONG_PHASE, "You can't do that now.");
                    }
                    me.setReady(ready);
                    broadcaster.snapshot(room, false);
                });
    }

    /** A spectator takes a free seat (lobby or podium only). */
    public void becomePlayer(String principal) {
        withMember(
                principal,
                (room, me) -> {
                    if (me.isPlayer()) {
                        return;
                    }
                    if (room.phase() != RoomPhase.LOBBY && room.phase() != RoomPhase.FINISHED) {
                        me.setWantsToPlay(true);
                        throw new RoomException(
                                ErrorCode.WRONG_PHASE,
                                "You'll join automatically after this game.");
                    }
                    int maxPlayers = catalog.require(room.gameId()).info().maxPlayers();
                    if (room.players().size() >= maxPlayers) {
                        throw new RoomException(ErrorCode.ROOM_FULL, "All seats are taken.");
                    }
                    me.setRole(Member.Role.PLAYER);
                    me.setWantsToPlay(true);
                    broadcaster.snapshot(room, true);
                });
    }

    /** The host starts the game: a short countdown, then the first turn. */
    public void start(String principal) {
        withMember(
                principal,
                (room, me) -> {
                    requireHost(room, me);
                    if (room.phase() != RoomPhase.LOBBY) {
                        throw new RoomException(ErrorCode.WRONG_PHASE, "The game already started.");
                    }
                    GameDefinition definition = catalog.require(room.gameId());
                    long online = room.players().stream().filter(Member::online).count();
                    if (online < definition.info().minPlayers()) {
                        throw new RoomException(
                                ErrorCode.NOT_ENOUGH_PLAYERS,
                                definition.info().name()
                                        + " needs at least "
                                        + definition.info().minPlayers()
                                        + " players.");
                    }
                    if (room.players().size() > definition.info().maxPlayers()) {
                        throw new RoomException(
                                ErrorCode.TOO_MANY_PLAYERS,
                                definition.info().name()
                                        + " allows at most "
                                        + definition.info().maxPlayers()
                                        + " players.");
                    }
                    Duration countdown = properties.timing().startCountdown();
                    room.setPhase(RoomPhase.STARTING);
                    room.setStartingWindow(now(), now() + countdown.toMillis());
                    room.setStartTimer(scheduler.schedule(room, countdown, () -> beginGame(room)));
                    broadcaster.snapshot(room, true);
                });
    }

    /** The host goes back to the lobby after the podium (same game and settings). */
    public void playAgain(String principal) {
        withMember(
                principal,
                (room, me) -> {
                    requireHost(room, me);
                    if (room.phase() != RoomPhase.FINISHED) {
                        throw new RoomException(ErrorCode.WRONG_PHASE, "The game isn't over yet.");
                    }
                    backToLobby(room);
                    broadcaster.snapshot(room, true);
                });
    }

    private void backToLobby(Room room) {
        room.setRun(null);
        room.setPhase(RoomPhase.LOBBY);
        int maxPlayers = catalog.require(room.gameId()).info().maxPlayers();
        for (Member spectator : room.spectators()) {
            if (spectator.wantsToPlay() && room.players().size() < maxPlayers) {
                spectator.setRole(Member.Role.PLAYER);
            }
        }
        room.members().forEach(m -> m.setReady(false));
    }

    private void beginGame(Room room) {
        if (room.phase() != RoomPhase.STARTING) {
            return;
        }
        room.setStartTimer(null);
        List<Member> players = room.players();
        if (players.isEmpty()) {
            return;
        }
        GameDefinition definition = catalog.require(room.gameId());
        GameRun run =
                GameRun.builder()
                        .roomCode(room.code())
                        .gameId(definition.info().id())
                        .settings(room.settings())
                        .logic(definition.createLogic(room.settings()))
                        .players(players.stream().map(Member::toPlayerInfo).toList())
                        .random(new Random())
                        .clock(clock)
                        .timers(scheduler.timersFor(room))
                        .converter(converter)
                        .config(
                                new GameRunConfig(
                                        properties.timing().reveal(), Duration.ofSeconds(60)))
                        .listener(new RoomGameListener(room, definition.info().name()))
                        .build();
        room.setRun(run);
        players.forEach(
                p -> {
                    p.setReady(false);
                    if (!p.online()) {
                        run.setConnected(p.id(), false);
                    }
                });
        metrics.gameStarted(definition.info().id());
        run.start();
    }

    // ================================================================== game

    /** Forwards a game move from the caller to the running game. */
    public void act(String principal, String type, Object payload) {
        withMember(
                principal,
                (room, me) -> {
                    if (!me.limiter().tryAcquire(now())) {
                        throw new RoomException(ErrorCode.RATE_LIMITED, "Slow down a little!");
                    }
                    GameRun run = room.run();
                    if (run == null
                            || (room.phase() != RoomPhase.PLAYING
                                    && room.phase() != RoomPhase.REVEAL)) {
                        throw new RoomException(ErrorCode.WRONG_PHASE, "No game is running.");
                    }
                    if (!run.isPlaying(me.id())) {
                        throw new RoomException(
                                ErrorCode.INVALID_ACTION, "You're watching this game.");
                    }
                    run.act(me.id(), type, payload);
                });
    }

    private final class RoomGameListener implements GameRunListener {
        private final Room room;
        private final String gameName;

        private RoomGameListener(Room room, String gameName) {
            this.room = room;
            this.gameName = gameName;
        }

        @Override
        public void changed(boolean urgent) {
            GameRun run = room.run();
            if (run != null && run.phase() != null && room.phase() != RoomPhase.LOBBY) {
                room.setPhase(
                        switch (run.phase()) {
                            case PLAYING -> RoomPhase.PLAYING;
                            case REVEAL -> RoomPhase.REVEAL;
                            case FINISHED -> RoomPhase.FINISHED;
                        });
            }
            broadcaster.snapshot(room, urgent);
        }

        @Override
        public void stream(String type, Object payload) {
            broadcaster.stream(room, type, payload);
        }

        @Override
        public void sendTo(String playerId, String type, Object payload) {
            room.member(playerId)
                    .ifPresent(m -> broadcaster.event(m, ServerEvent.game(type, payload)));
        }

        @Override
        public void finished(GameResult result) {
            room.setPhase(RoomPhase.FINISHED);
            room.recordCup(result);
            history.recordAsync(room, gameName, result);
            metrics.gameFinished(room.gameId());
            LOG.info("Game {} finished in room {}", room.gameId(), room.code());
        }
    }

    // ================================================================== presence

    /** A WebSocket closed. The member keeps their seat for the grace period. */
    public void disconnected(String principal) {
        Optional<RoomStore.Binding> binding = store.binding(principal);
        if (binding.isEmpty()) {
            return;
        }
        Optional<Room> found = store.find(binding.get().code());
        if (found.isEmpty()) {
            store.unbind(principal);
            return;
        }
        Room room = found.get();
        room.locked(
                () -> {
                    store.unbind(principal);
                    Member member = room.member(binding.get().memberId()).orElse(null);
                    if (member == null || !principal.equals(member.principal())) {
                        return;
                    }
                    member.setOnline(false);
                    member.setPrincipal(null);
                    long epoch = member.nextPresenceEpoch();
                    if (room.run() != null) {
                        room.run().setConnected(member.id(), false);
                    }
                    scheduler.schedule(
                            room,
                            properties.timing().presenceGrace(),
                            () ->
                                    room.member(member.id())
                                            .filter(m -> !m.online() && m.presenceEpoch() == epoch)
                                            .ifPresent(m -> removeMember(room, m, "timed out")));
                    broadcaster.snapshot(room, true);
                });
    }

    /** Closes rooms nobody uses anymore (runs every minute). */
    public void evictStaleRooms() {
        long now = now();
        AppProperties.Rooms limits = properties.rooms();
        for (Room room : store.all()) {
            room.locked(
                    () -> {
                        if (room.closed()) {
                            return;
                        }
                        long idle = now - room.lastActivity();
                        if (!room.hasOnlineMembers() && idle > limits.idleTimeout().toMillis()) {
                            closeRoom(room, "Room closed after everyone disconnected.");
                        } else if (idle > limits.inactiveTimeout().toMillis()) {
                            closeRoom(room, "Room closed after a long break.");
                        } else if (now - room.createdAt() > limits.maxAge().toMillis()) {
                            closeRoom(room, "Room closed after reaching its maximum age.");
                        }
                    });
        }
    }

    // ================================================================== helpers

    private void withMember(String principal, BiConsumer<Room, Member> action) {
        RoomStore.Binding binding =
                store.binding(principal)
                        .orElseThrow(
                                () ->
                                        new RoomException(
                                                ErrorCode.NOT_IN_ROOM, "You're not in a room."));
        Room room =
                store.find(binding.code())
                        .orElseThrow(
                                () ->
                                        new RoomException(
                                                ErrorCode.SESSION_EXPIRED,
                                                "This room doesn't exist anymore."));
        room.locked(
                () -> {
                    if (room.closed()) {
                        throw new RoomException(
                                ErrorCode.SESSION_EXPIRED, "This room doesn't exist anymore.");
                    }
                    Member member =
                            room.member(binding.memberId())
                                    .orElseThrow(
                                            () ->
                                                    new RoomException(
                                                            ErrorCode.NOT_IN_ROOM,
                                                            "You're not in this room anymore."));
                    MDC.put("room", room.code());
                    try {
                        room.touch(now());
                        action.accept(room, member);
                    } finally {
                        MDC.remove("room");
                    }
                });
    }

    private static void requireHost(Room room, Member member) {
        if (!room.isHost(member)) {
            throw new RoomException(ErrorCode.NOT_HOST, "Only the host can do that.");
        }
    }

    private static RoomException notFound(String code) {
        return new RoomException(
                ErrorCode.ROOM_NOT_FOUND, "There is no room with the code " + code + ".");
    }

    private RateLimiter limiter() {
        AppProperties.RateLimit limit = properties.rateLimit();
        return new RateLimiter(limit.capacity(), limit.refillPerSecond(), now());
    }

    private long now() {
        return clock.millis();
    }
}
