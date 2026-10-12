package com.example.funfridaygame.web;

import com.example.funfridaygame.engine.RoomQueries;
import com.example.funfridaygame.engine.RoomQueries.RoomSummary;
import com.example.funfridaygame.history.HistoryService;
import com.example.funfridaygame.history.HistoryService.GameHistoryEntry;
import com.example.funfridaygame.history.HistoryService.LeaderboardEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST: public room facts (for the join page), game history and the leaderboard. */
@RestController
@RequestMapping("/api")
@Tag(name = "Rooms & history")
public class RoomQueryController {

    private final RoomQueries rooms;
    private final HistoryService history;

    public RoomQueryController(RoomQueries rooms, HistoryService history) {
        this.rooms = rooms;
        this.history = history;
    }

    @GetMapping("/rooms/{code}")
    @Operation(summary = "Public facts about a live room (404 if it doesn't exist)")
    public ResponseEntity<RoomSummary> room(@PathVariable String code) {
        return ResponseEntity.of(rooms.summary(code));
    }

    @GetMapping("/rooms/{code}/history")
    @Operation(summary = "Games played in a live room, newest first")
    public ResponseEntity<List<GameHistoryEntry>> roomHistory(@PathVariable String code) {
        return ResponseEntity.of(rooms.instanceId(code).map(history::roomHistory));
    }

    @GetMapping("/history")
    @Operation(summary = "The latest finished games on this server")
    public List<GameHistoryEntry> recent() {
        return history.recent();
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Best players by Friday Cup points")
    public List<LeaderboardEntry> leaderboard(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return history.leaderboard(days, limit);
    }
}
