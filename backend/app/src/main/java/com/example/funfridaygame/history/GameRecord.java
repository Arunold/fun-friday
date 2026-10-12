package com.example.funfridaygame.history;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A finished game, stored for room history, the Friday Cup and the leaderboard.
 *
 * <p>Explicit names are upper case because Spring Data JDBC quotes identifiers and H2 stores
 * unquoted DDL names in upper case.
 */
@Table("GAME_RECORD")
public class GameRecord {

    @Id private Long id;
    private String roomId;
    private String roomCode;
    private String gameId;
    private String gameName;
    private int rounds;
    private String settingsJson;
    private Instant startedAt;
    private Instant finishedAt;

    @MappedCollection(idColumn = "GAME_RECORD_ID")
    private Set<GameRecordPlayer> players = new HashSet<>();

    protected GameRecord() {}

    public GameRecord(
            String roomId,
            String roomCode,
            String gameId,
            String gameName,
            int rounds,
            String settingsJson,
            Instant startedAt,
            Instant finishedAt,
            Set<GameRecordPlayer> players) {
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.gameId = gameId;
        this.gameName = gameName;
        this.rounds = rounds;
        this.settingsJson = settingsJson;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.players = new HashSet<>(players);
    }

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getGameId() {
        return gameId;
    }

    public String getGameName() {
        return gameName;
    }

    public int getRounds() {
        return rounds;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Set<GameRecordPlayer> getPlayers() {
        return players;
    }
}
