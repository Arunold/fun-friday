package com.example.funfridaygame.games.snakearena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for the end of a round: shared headlines, a round without points, several
 * survivors of a last-snake-standing round, and how ties for an award are broken (the higher score
 * first, then whoever joined first).
 *
 * <p>The snakes circle rectangles (see {@link TestArena#circle}) so they live through the whole
 * round and score exactly what each test feeds them.
 */
class SnakeArenaScoringTest {

    private static final int LONGEST = RoundScoring.LONGEST_BONUS;
    private static final int SURVIVOR = RoundScoring.SURVIVOR_BONUS;

    @Test
    void equalTopScoresShareTheHeadlineAndAwardTiesGoToWhoJoinedFirst() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice", "bob").calm().go();
        circleWithOneApple(arena, "alice", 4, 3);
        circleWithOneApple(arena, "bob", 20, 12);

        arena.playOut();

        assertThat(arena.game.lastTurn().points())
                .containsEntry("alice", 1 + LONGEST)
                .containsEntry("bob", 1 + LONGEST);
        assertThat(arena.game.lastTurn().headline())
                .isEqualTo("alice and bob shared the arena with 6 points each");
        assertThat(results(arena))
                .extracting(row -> row.get("playerId"), row -> row.get("longest"))
                .containsExactly(tuple("alice", true), tuple("bob", true));

        arena.game.finishGame();
        assertThat(arena.game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Biggest snake", "alice", "Grew to 5 segments"),
                        tuple("Glutton", "alice", "Ate 1 apple"),
                        tuple("Survivor", "alice", "No crashes"));
    }

    @Test
    void aRoundWithoutPointsSaysSoAndCrashedSnakesGetNoBonus() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 8, 3, 7, 3, 6, 3, 5, 3)
                .circle("alice", 4, 3, 12, 8)
                .place("bob", Dir.RIGHT, 24, 12, 23, 12, 22, 12, 21, 12)
                .circle("bob", 20, 12, 28, 18)
                .until(580);

        // Both crash into a wall late enough that they are still waiting when the time is up.
        arena.release("alice")
                .release("bob")
                .place("alice", Dir.LEFT, 0, 3, 1, 3, 2, 3, 3, 3)
                .place("bob", Dir.RIGHT, 35, 20, 34, 20, 33, 20, 32, 20)
                .playOut();

        assertThat(arena.game.lastTurn().points())
                .containsEntry("alice", 0)
                .containsEntry("bob", 0);
        assertThat(arena.game.lastTurn().headline())
                .isEqualTo("No points this round — the snakes just wandered");
        assertThat(results(arena))
                .allSatisfy(row -> assertThat(row).containsEntry("longest", false));
    }

    @Test
    void everySurvivorOfALastSnakeStandingRoundScoresAndTheTopScorerWinsAwardTies() {
        TestArena arena =
                TestArena.start(
                                Map.of("roundDuration", 60, "variation", "last-standing"),
                                "alice",
                                "bob",
                                "carol",
                                "dan")
                        .calm()
                        .go();
        circleWithOneApple(arena, "alice", 4, 3);
        circleWithOneApple(arena, "bob", 20, 12);
        circleWithOneApple(arena, "dan", 4, 15);
        // Carol drives straight into Bob's body: out for the round, +5 for Bob.
        arena.place("carol", Dir.DOWN, 22, 11, 22, 10, 22, 9, 22, 8).ticks(1);
        assertThat(arena.frameSnake("carol")).containsEntry("alive", false);
        assertThat(arena.feed().getLast())
                .containsEntry("kind", "BODY")
                .containsEntry("a", "bob")
                .containsEntry("b", "carol");

        arena.playOut();

        assertThat(arena.sim().tick()).as("three snakes left: played to the end").isEqualTo(599);
        int survivor = 1 + LONGEST + SURVIVOR;
        assertThat(arena.game.lastTurn().points())
                .containsEntry("alice", survivor)
                .containsEntry("bob", survivor + Simulation.BODY_CREDIT)
                .containsEntry("carol", 0)
                .containsEntry("dan", survivor);
        assertThat(arena.game.lastTurn().headline()).isEqualTo("alice, bob and dan survived!");

        arena.game.finishGame();
        assertThat(arena.game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(arena.game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Biggest snake", "bob", "Grew to 5 segments"),
                        tuple("Trapper", "bob", "1 snake ran into them"),
                        tuple("Glutton", "bob", "Ate 1 apple"),
                        tuple("Survivor", "bob", "No crashes"));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Lays a snake on the top edge of an 8×5 (… 6) rectangle whose top-left corner is (x, y), keeps
     * it circling and puts one apple two cells ahead of its head.
     */
    private static void circleWithOneApple(TestArena arena, String id, int x, int y) {
        arena.place(id, Dir.RIGHT, x + 4, y, x + 3, y, x + 2, y, x + 1, y)
                .circle(id, x, y, x + 8, y + 5)
                .food(x + 6, y);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(TestArena arena) {
        return (List<Map<String, Object>>) arena.game.spectatorView().get("results");
    }
}
