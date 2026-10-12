package com.example.funfridaygame.web;

import com.example.funfridaygame.engine.RoomQueries;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint exposing secret game state (e.g. the number to guess) so end-to-end tests can
 * play deterministically. Only exists with {@code app.e2e.enabled=true} (the {@code e2e} profile).
 */
@Hidden
@RestController
@ConditionalOnProperty(prefix = "app.e2e", name = "enabled", havingValue = "true")
public class E2eController {

    private final RoomQueries rooms;

    public E2eController(RoomQueries rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/api/e2e/rooms/{code}")
    public ResponseEntity<Map<String, Object>> state(@PathVariable String code) {
        return ResponseEntity.of(rooms.debugState(code));
    }
}
