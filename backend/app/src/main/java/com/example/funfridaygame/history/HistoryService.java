package com.example.funfridaygame.history;

import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.runtime.GameResult;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists finished games (off the room lock, on a virtual thread) and answers history and
 * leaderboard queries.
 */
@Service
public class HistoryService implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(HistoryService.class);

    private final GameRecordRepository repository;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final InstantSource clock;
    private final ExecutorService writer = Executors.newVirtualThreadPerTaskExecutor();

    public HistoryService(
            GameRecordRepository repository,
            JdbcClient jdbc,
            JsonMapper json,
            InstantSource clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * A finished game as shown in history lists.
     *
     * @param gameId game id
     * @param gameName display name
     * @param finishedAt when it ended
     * @param players results, best first
     */
    public record GameHistoryEntry(
            String gameId, String gameName, Instant finishedAt, List<GameRecordPlayer> players) {}

    /**
     * A leaderboard line (players are identified by name; there are no accounts).
     *
     * @param name display name
     * @param avatar latest emoji
     * @param points Friday Cup points
     * @param games games played
     * @param wins games won
     */
    public record LeaderboardEntry(String name, String avatar, int points, int games, int wins) {}

    /** Stores a finished game asynchronously. Copy everything needed while holding the lock. */
    public void recordAsync(Room room, String gameName, GameResult result) {
        Set<GameRecordPlayer> players = new HashSet<>();
        for (GameResult.Standing standing : result.standings()) {
            players.add(
                    new GameRecordPlayer(
                            standing.player().name(),
                            standing.player().avatar(),
                            standing.score(),
                            standing.rank(),
                            Room.cupPoints(standing.rank())));
        }
        Map<String, Object> settings = room.settings().asMap();
        GameRecord record =
                new GameRecord(
                        room.instanceId(),
                        room.code(),
                        room.gameId(),
                        gameName,
                        room.settings().rounds(),
                        json.writeValueAsString(settings),
                        result.startedAt() == null ? result.finishedAt() : result.startedAt(),
                        result.finishedAt(),
                        players);
        writer.submit(
                () -> {
                    try {
                        repository.save(record);
                    } catch (RuntimeException e) {
                        LOG.error("Could not store game of room {}", record.getRoomCode(), e);
                    }
                });
    }

    /** The latest games of a room instance. */
    public List<GameHistoryEntry> roomHistory(String roomInstanceId) {
        return repository.findTop20ByRoomIdOrderByFinishedAtDesc(roomInstanceId).stream()
                .map(HistoryService::entry)
                .toList();
    }

    /** The latest games overall. */
    public List<GameHistoryEntry> recent() {
        return repository.findTop20ByOrderByFinishedAtDesc().stream()
                .map(HistoryService::entry)
                .toList();
    }

    /** Best players of the last {@code days} days by Friday Cup points. */
    public List<LeaderboardEntry> leaderboard(int days, int limit) {
        Instant since = clock.instant().minus(Duration.ofDays(Math.max(1, days)));
        return jdbc.sql(
                        """
                        SELECT MAX(p.player_name) AS name, MAX(p.avatar) AS avatar,
                               SUM(p.cup_points) AS points, COUNT(*) AS games,
                               SUM(CASE WHEN p.placement = 1 THEN 1 ELSE 0 END) AS wins
                        FROM game_record_player p
                        JOIN game_record g ON g.id = p.game_record_id
                        WHERE g.finished_at >= :since
                        GROUP BY LOWER(p.player_name)
                        ORDER BY points DESC, wins DESC, name
                        LIMIT :limit
                        """)
                .param("since", since)
                .param("limit", Math.max(1, Math.min(limit, 100)))
                .query(
                        (rs, i) ->
                                new LeaderboardEntry(
                                        rs.getString("name"),
                                        rs.getString("avatar"),
                                        rs.getInt("points"),
                                        rs.getInt("games"),
                                        rs.getInt("wins")))
                .list();
    }

    private static GameHistoryEntry entry(GameRecord record) {
        List<GameRecordPlayer> players =
                record.getPlayers().stream()
                        .sorted(
                                Comparator.comparingInt(GameRecordPlayer::placement)
                                        .thenComparing(GameRecordPlayer::playerName))
                        .toList();
        return new GameHistoryEntry(
                record.getGameId(), record.getGameName(), record.getFinishedAt(), players);
    }

    @Override
    public void destroy() {
        writer.close();
    }
}
