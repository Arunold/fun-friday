package com.example.funfridaygame.games.__PACKAGE__;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays __GAME_NAME__ through the real engine with virtual time. */
class __CLASS__GameTest {

    private GameHarness game() {
        return GameHarness.of(new __CLASS__Game())
                .players("alice", "bob")
                .settings(Map.of("rounds", 1))
                .seed(7)
                .start();
    }

    @Test
    void exactPickWinsAndTheTurnEndsWhenEveryonePicked() {
        GameHarness game = game();
        int lucky = (Integer) game.debugState().get("lucky");

        game.act("alice", "pick", Map.of("value", lucky));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.act("bob", "pick", Map.of("value", lucky == 1 ? 2 : lucky - 1));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsEntry("alice", 20);
    }

    @Test
    void picksAreSecretUntilTheReveal() {
        GameHarness game = game();
        game.act("alice", "pick", Map.of("value", 3));

        Map<String, Object> bobView = game.view("bob");
        assertThat(bobView.get("myPick")).isNull();
        assertThat(bobView.toString()).doesNotContain("value=3");
    }

    @Test
    void invalidPicksAreRejected() {
        GameHarness game = game();
        assertThat(game.reject("alice", "pick", Map.of("value", 99)).getMessage())
                .contains("1 to 10");
        game.act("alice", "pick", Map.of("value", 5));
        assertThat(game.reject("alice", "pick", Map.of("value", 6)).getMessage())
                .contains("already");
    }

    @Test
    void theDeadlineEndsTheTurn() {
        GameHarness game = game();
        game.advanceSeconds(16);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
    }
}
