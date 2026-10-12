package com.example.funfridaygame.games.numberguess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays Number Guess through the real engine with virtual time. */
class NumberGuessGameTest {

    private GameHarness game(String... players) {
        return GameHarness.of(new NumberGuessGame())
                .players(players)
                .settings(Map.of("rounds", 1, "attempts", 3))
                .seed(42)
                .start();
    }

    private int target(GameHarness game) {
        return (Integer) game.debugState().get("target");
    }

    @Test
    void exactHitScoresAndEndsTheTurnWhenEveryoneIsDone() {
        GameHarness game = game("alice", "bob");
        int target = target(game);

        game.act("alice", "guess", Map.of("value", target));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);

        int wrong = target == 1 ? 2 : target - 1;
        game.act("bob", "guess", Map.of("value", wrong))
                .act("bob", "guess", Map.of("value", wrong))
                .act("bob", "guess", Map.of("value", wrong));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("The number was " + target);
        assertThat(game.lastTurn().points().get("alice")).isGreaterThanOrEqualTo(100);
        assertThat(game.lastTurn().points().get("bob")).isBetween(1, 50);
    }

    @Test
    void hintsArePrivateUntilTheReveal() {
        GameHarness game = game("alice", "bob");
        int target = target(game);
        int low = target == 1 ? 1 : target - 1;

        game.act("alice", "guess", Map.of("value", low));

        Map<String, Object> aliceView = game.view("alice");
        List<?> mine = (List<?>) aliceView.get("myGuesses");
        assertThat(mine).hasSize(1);
        assertThat(aliceView.get("target")).isNull();

        Map<String, Object> bobView = game.view("bob");
        List<?> players = (List<?>) bobView.get("players");
        Map<?, ?> aliceStatus = (Map<?, ?>) players.getFirst();
        assertThat(aliceStatus.get("guessesUsed")).isEqualTo(1);
        assertThat(aliceStatus.get("guesses")).isNull();

        game.finishTurn();
        Map<?, ?> revealed = (Map<?, ?>) ((List<?>) game.view("bob").get("players")).getFirst();
        assertThat(revealed.get("guesses")).isNotNull();
        assertThat(game.view("bob").get("target")).isEqualTo(target);
    }

    @Test
    void rejectsInvalidGuesses() {
        GameHarness game = game("alice", "bob");
        int target = target(game);

        assertThat(game.reject("alice", "guess", Map.of("value", 0)).getMessage())
                .contains("from 1 to 100");
        assertThat(game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown");
        assertThat(game.reject("alice", "guess", Map.of()).getMessage()).contains("value");

        int wrong = target == 100 ? 99 : target + 1;
        game.act("alice", "guess", Map.of("value", wrong))
                .act("alice", "guess", Map.of("value", wrong))
                .act("alice", "guess", Map.of("value", wrong));
        assertThat(game.reject("alice", "guess", Map.of("value", target)).getMessage())
                .contains("no guesses left");
    }

    @Test
    void disconnectedOrDepartedPlayersDoNotBlockTheRound() {
        GameHarness game = game("alice", "bob", "carol");
        int target = target(game);

        game.act("alice", "guess", Map.of("value", target)).leave("alice");
        game.act("bob", "guess", Map.of("value", target));
        assertThat(game.phase()).as("carol still has to answer").isEqualTo(RunPhase.PLAYING);

        game.disconnect("carol");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).doesNotContainKey("alice");
    }

    @Test
    void playsAFullGameToThePodium() {
        GameHarness game =
                GameHarness.of(new NumberGuessGame())
                        .players("alice", "bob")
                        .settings(Map.of("rounds", 2))
                        .start();
        game.act("alice", "guess", Map.of("value", target(game))).finishTurn().nextTurn();
        assertThat(game.round()).isEqualTo(2);
        game.finishGame();

        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
        assertThat(game.result().awards()).extracting(a -> a.title()).contains("Bullseye");
    }

    @Test
    void validatesTheNumberRange() {
        assertThatThrownBy(
                        () ->
                                GameHarness.of(new NumberGuessGame())
                                        .players("alice", "bob")
                                        .settings(Map.of("minRange", 100, "maxRange", 50))
                                        .start())
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("at least 10 above");
    }
}
