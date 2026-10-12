package com.example.funfridaygame.engine;

import com.example.funfridaygame.catalog.GameCatalog;
import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.room.RoomPhase;
import com.example.funfridaygame.room.RoomStore;
import com.example.funfridaygame.runtime.GameRun;
import com.example.funfridaygame.sdk.GameInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only questions about live rooms (for REST endpoints); never exposes secrets. */
@Service
public class RoomQueries {

    private final RoomStore store;
    private final GameCatalog catalog;

    public RoomQueries(RoomStore store, GameCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    /**
     * Public facts about a room, for the join page (no secrets).
     *
     * @param code room code
     * @param phase room phase
     * @param gameId selected game
     * @param gameName selected game's name
     * @param gameIcon selected game's emoji
     * @param hostName host's name
     * @param players number of players
     * @param maxPlayers player limit of the selected game
     * @param joinable whether a new member would get a seat right away
     */
    public record RoomSummary(
            String code,
            RoomPhase phase,
            String gameId,
            String gameName,
            String gameIcon,
            String hostName,
            int players,
            int maxPlayers,
            boolean joinable) {}

    /** The summary of a live room, or empty if there is no such room. */
    public Optional<RoomSummary> summary(String code) {
        return store.find(code).flatMap(room -> room.locked(() -> summaryOf(room)));
    }

    private Optional<RoomSummary> summaryOf(Room room) {
        if (room.closed()) {
            return Optional.empty();
        }
        GameInfo info = catalog.require(room.gameId()).info();
        int players = room.players().size();
        boolean betweenGames =
                room.phase() == RoomPhase.LOBBY || room.phase() == RoomPhase.FINISHED;
        String hostName = room.member(room.hostId()).map(Member::name).orElse("");
        return Optional.of(
                new RoomSummary(
                        room.code(),
                        room.phase(),
                        info.id(),
                        info.name(),
                        info.icon(),
                        hostName,
                        players,
                        info.maxPlayers(),
                        betweenGames && players < info.maxPlayers()));
    }

    /** The room instance id behind a live code (for history lookups). */
    public Optional<String> instanceId(String code) {
        return store.find(code).map(Room::instanceId);
    }

    /** Internal game state for end-to-end tests (exposed only with {@code app.e2e.enabled}). */
    public Optional<Map<String, Object>> debugState(String code) {
        return store.find(code).map(room -> room.locked(() -> debugStateOf(room)));
    }

    private static Map<String, Object> debugStateOf(Room room) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", room.phase());
        state.put("gameId", room.gameId());
        GameRun run = room.run();
        if (run != null) {
            state.put("round", run.round());
            state.put("turn", run.turn());
            state.put("state", run.debugState());
        }
        return state;
    }
}
