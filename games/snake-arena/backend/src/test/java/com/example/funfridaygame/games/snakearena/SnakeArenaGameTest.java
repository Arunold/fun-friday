package com.example.funfridaygame.games.snakearena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.games.snakearena.SnakeArenaView.FrameView;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for Snake Arena's rounds: start positions, frames and snapshots, the round end
 * with its bonuses and headline, last snake standing, several rounds, players leaving and losing
 * the connection, the reveal and the awards.
 */
class SnakeArenaGameTest {

    // ------------------------------------------------------------------ start & views

    @Test
    void aRoundStartsWithSnakesOnFixedSlotsAndFood() {
        TestArena arena = TestArena.start("alice", "bob");

        Map<String, Object> view = arena.game.view("alice");
        assertThat(view)
                .containsEntry("cols", 36)
                .containsEntry("rows", 24)
                .containsEntry("arena", "SMALL")
                .containsEntry("variation", "classic")
                .containsEntry("powers", false)
                .containsEntry("tickMs", 100)
                .containsEntry("startDelay", Simulation.START_DELAY_TICKS)
                .containsEntry("respawnTicks", Simulation.RESPAWN_TICKS);
        assertThat(view.get("results")).isNull();
        assertThat(arena.frame()).containsEntry("round", 1).containsEntry("tick", 0);
        assertThat(arena.frameSnake("alice"))
                .containsEntry("body", cells(arena, 6, 7, 5, 7, 4, 7, 3, 7))
                .containsEntry("dir", "RIGHT")
                .containsEntry("alive", true)
                .containsEntry("points", 0)
                .containsEntry("length", 4);
        assertThat(arena.frameSnake("bob"))
                .containsEntry("body", cells(arena, 29, 16, 30, 16, 31, 16, 32, 16))
                .containsEntry("dir", "LEFT");
        assertThat((List<?>) arena.frame().get("food")).hasSize(5);
        assertThat((List<?>) view.get("roster")).hasSize(2);
    }

    @Test
    void everybodySeesTheSameArena() {
        TestArena arena = TestArena.start("alice", "bob", "carol");
        arena.ticks(15);

        assertThat(arena.game.view("alice"))
                .isEqualTo(arena.game.view("bob"))
                .isEqualTo(arena.game.view("carol"))
                .isEqualTo(arena.game.spectatorView());
    }

    @Test
    void theArenaGrowsWithThePlayerCountUnlessTheHostPicksASize() {
        assertThat(TestArena.start("a", "b", "c", "d").game.view("a"))
                .containsEntry("arena", "MEDIUM");
        assertThat(TestArena.start("a", "b", "c", "d", "e", "f", "g").game.view("a"))
                .containsEntry("arena", "LARGE")
                .containsEntry("cols", 52)
                .containsEntry("rows", 32);
        assertThat(TestArena.start(Map.of("arena", "large"), "a", "b").game.view("a"))
                .containsEntry("arena", "LARGE");
        assertThat(
                        TestArena.start(
                                        Map.of("arena", "small"),
                                        "a",
                                        "b",
                                        "c",
                                        "d",
                                        "e",
                                        "f",
                                        "g",
                                        "h",
                                        "i",
                                        "j",
                                        "k",
                                        "l")
                                .game
                                .view("a"))
                .containsEntry("arena", "SMALL");
    }

    @Test
    void ticksStreamFramesWithoutSnapshots() {
        TestArena arena = TestArena.start("alice", "bob");
        int changes = arena.game.changes();
        int before = arena.streamedFrames().size();

        arena.ticks(20);

        assertThat(arena.game.changes()).as("no snapshots while the round runs").isEqualTo(changes);
        List<FrameView> frames = arena.streamedFrames().subList(before, before + 20);
        assertThat(frames).extracting(FrameView::tick).containsExactlyElementsOf(range(1, 20));
        assertThat(frames).allSatisfy(frame -> assertThat(frame.round()).isEqualTo(1));
        assertThat(arena.frame()).containsEntry("tick", 20);
        assertThat(frames.getLast().snakes().getFirst().body())
                .containsExactly(
                        arena.snake("alice").body().stream().mapToInt(Integer::intValue).toArray());
    }

    // ------------------------------------------------------------------ round end

    @Test
    void theRoundEndsWithTheTimerAndTheHeadlineNamesTheTopScorer() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice").calm();
        arena.go().food(7, 12).food(8, 12).food(9, 12).ticks(3);

        arena.ticks(586);
        assertThat(arena.game.phase()).isEqualTo(RunPhase.PLAYING);
        arena.game.finishTurn();

        assertThat(arena.sim().tick()).as("the last tick before the deadline").isEqualTo(599);
        int points = arena.game.lastTurn().points().get("alice");
        assertThat(points).isGreaterThanOrEqualTo(3);
        assertThat(arena.game.lastTurn().headline())
                .isEqualTo("alice ruled the arena with " + points + " points");
        assertThat(arena.game.score("alice")).isEqualTo(points);
    }

    @Test
    void theLongestLivingSnakesShareTheBonus() {
        TestArena arena =
                TestArena.start(Map.of("roundDuration", 60), "alice", "bob", "carol", "dan");
        arena.calm().ticks(590);
        arena.place("alice", Dir.RIGHT, 10, 2, 9, 2, 8, 2, 7, 2, 6, 2, 5, 2)
                .place("bob", Dir.RIGHT, 10, 5, 9, 5, 8, 5, 7, 5, 6, 5, 5, 5)
                .place("carol", Dir.RIGHT, 10, 8, 9, 8, 8, 8, 7, 8)
                .place("dan", Dir.RIGHT, 43, 11, 42, 11, 41, 11, 40, 11, 39, 11, 38, 11, 37, 11);
        Map<String, Integer> before =
                Map.of(
                        "alice", arena.snake("alice").points(),
                        "bob", arena.snake("bob").points(),
                        "carol", arena.snake("carol").points(),
                        "dan", arena.snake("dan").points());

        arena.game.finishTurn(); // dan (longer) hits the wall before the end

        assertThat(arena.snake("dan").alive()).isFalse();
        Map<String, Integer> points = arena.game.lastTurn().points();
        assertThat(points.get("alice")).isEqualTo(before.get("alice") + RoundScoring.LONGEST_BONUS);
        assertThat(points.get("bob")).isEqualTo(before.get("bob") + RoundScoring.LONGEST_BONUS);
        assertThat(points.get("carol")).isEqualTo(before.get("carol"));
        assertThat(points.get("dan")).isEqualTo(before.get("dan"));
        assertThat(results(arena.game.view("carol")))
                .filteredOn(row -> Boolean.TRUE.equals(row.get("longest")))
                .extracting(row -> row.get("playerId"))
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void theRevealFreezesTheFinalFrameAndShowsTheRoundTable() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice", "bob");
        arena.game.finishTurn();
        long tick = arena.sim().tick();

        Map<String, Object> view = arena.game.view("bob");
        List<Map<String, Object>> rows = results(view);
        assertThat(rows)
                .extracting(row -> row.get("playerId"))
                .containsExactlyInAnyOrder("alice", "bob");
        assertThat(rows.getFirst().get("points")).isEqualTo(maxPoints(arena));
        assertThat(rows.getFirst())
                .containsKeys("food", "credits", "crashes", "maxLength", "longest", "survivor");
        assertThat(arena.frameSnake("alice").get("points"))
                .as("the frozen frame includes the bonuses")
                .isEqualTo(arena.game.lastTurn().points().get("alice"));

        arena.ticks(20); // the reveal: nothing moves any more
        assertThat(arena.sim().tick()).isEqualTo(tick);
    }

    // ------------------------------------------------------------------ last snake standing

    @Test
    void lastSnakeStandingEndsTheRoundEarlyAndRewardsTheSurvivor() {
        TestArena arena =
                TestArena.start(Map.of("variation", "last-standing"), "alice", "bob", "carol")
                        .calm()
                        .go();
        assertThat(arena.game.view("alice")).containsEntry("respawnTicks", 0);

        arena.place("alice", Dir.RIGHT, 35, 2, 34, 2, 33, 2, 32, 2).ticks(1);
        assertThat(arena.game.phase()).as("two snakes are left").isEqualTo(RunPhase.PLAYING);
        assertThat(arena.frameSnake("alice").get("respawnAt")).as("no respawns").isNull();

        arena.place("bob", Dir.LEFT, 0, 20, 1, 20, 2, 20, 3, 20).ticks(1);

        assertThat(arena.game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(arena.game.lastTurn().points())
                .containsEntry("carol", RoundScoring.SURVIVOR_BONUS + RoundScoring.LONGEST_BONUS)
                .containsEntry("alice", 0)
                .containsEntry("bob", 0);
        assertThat(arena.game.lastTurn().headline()).isEqualTo("carol survived!");
        assertThat(results(arena.game.view("alice")).getFirst())
                .containsEntry("playerId", "carol")
                .containsEntry("survivor", true)
                .containsEntry("longest", true);
        arena.ticks(40);
        assertThat(arena.snake("alice").alive()).isFalse();
    }

    @Test
    void nobodySurvivesWhenTheLastTwoCrashTogether() {
        TestArena arena =
                TestArena.start(Map.of("variation", "last-standing"), "alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 35, 2, 34, 2, 33, 2, 32, 2)
                .place("bob", Dir.LEFT, 0, 20, 1, 20, 2, 20, 3, 20)
                .ticks(1);

        assertThat(arena.game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(arena.game.lastTurn().headline()).isEqualTo("Nobody survived!");
        assertThat(arena.game.lastTurn().points())
                .containsEntry("alice", 0)
                .containsEntry("bob", 0);
    }

    // ------------------------------------------------------------------ several rounds

    @Test
    void roundsAddUpAndTheStartSlotsRotate() {
        TestArena arena = TestArena.start(Map.of("rounds", 2, "roundDuration", 60), "alice", "bob");
        assertThat(arena.head("alice")).containsExactly(6, 7);
        assertThat(arena.head("bob")).containsExactly(29, 16);
        arena.game.finishTurn();
        Map<String, Integer> first = arena.game.lastTurn().points();

        arena.game.nextTurn();
        assertThat(arena.game.round()).isEqualTo(2);
        assertThat(arena.frame()).containsEntry("round", 2).containsEntry("tick", 0);
        assertThat(arena.head("alice")).containsExactly(29, 16);
        assertThat(arena.head("bob")).containsExactly(6, 7);
        assertThat(arena.frameSnake("alice")).containsEntry("points", 0);

        arena.game.finishTurn();
        Map<String, Integer> second = arena.game.lastTurn().points();
        arena.game.finishGame();
        assertThat(arena.game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(arena.game.result().history()).hasSize(2);
        for (String player : List.of("alice", "bob")) {
            assertThat(arena.game.score(player)).isEqualTo(first.get(player) + second.get(player));
        }
    }

    // ------------------------------------------------------------------ leaving & connections

    @Test
    void aPlayerWhoLeavesTurnsIntoFoodAndIsGone() {
        TestArena arena = TestArena.start("alice", "bob", "carol").calm().go();
        arena.place("bob", Dir.RIGHT, 15, 12, 14, 12, 13, 12, 12, 12, 11, 12, 10, 12);

        arena.game.leave("bob");

        assertThat(arena.game.phase()).isEqualTo(RunPhase.PLAYING);
        assertThat(snakes(arena.frame()))
                .extracting(snake -> snake.get("id"))
                .containsExactly("alice", "carol");
        assertThat(arena.board().food())
                .containsExactly(arena.cell(15, 12), arena.cell(13, 12), arena.cell(11, 12));
        assertThat(arena.feed().getLast()).containsEntry("kind", "LEFT").containsEntry("a", "bob");
        assertThat((List<?>) arena.game.view("alice").get("roster"))
                .as("names stay known for the feed")
                .hasSize(3);

        arena.game.finishTurn();
        assertThat(arena.game.lastTurn().points()).containsOnlyKeys("alice", "carol");
    }

    @Test
    void theLastButOneLeavingEndsALastSnakeStandingRound() {
        TestArena arena = TestArena.start(Map.of("variation", "last-standing"), "alice", "bob");

        arena.game.leave("bob");

        assertThat(arena.game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(arena.game.lastTurn().headline()).isEqualTo("alice survived!");
    }

    @Test
    void departedPlayersDropOutOfTheRoundTable() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice", "bob", "carol");
        arena.game.finishTurn();

        arena.game.leave("carol");

        assertThat(results(arena.game.view("alice")))
                .extracting(row -> row.get("playerId"))
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void disconnectedSnakesKeepGoingStraightAndRespawn() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.game.disconnect("bob");

        arena.ticks(5);
        assertThat(arena.head("bob")).containsExactly(24, 16);
        assertThat(arena.game.phase()).isEqualTo(RunPhase.PLAYING);

        arena.ticks(25); // tick 40: x = -1
        assertThat(arena.snake("bob").alive()).isFalse();
        assertThat(arena.snake("bob").respawnAt()).isEqualTo(70);
        arena.ticks(30);
        assertThat(arena.snake("bob").alive()).isTrue();

        arena.game.reconnect("bob");
        Dir heading = arena.snake("bob").dir();
        Dir turn = heading == Dir.UP || heading == Dir.DOWN ? Dir.LEFT : Dir.UP;
        arena.turn("bob", turn.name()).ticks(1);
        assertThat(arena.snake("bob").dir()).isEqualTo(turn);
    }

    // ------------------------------------------------------------------ podium

    @Test
    void awardsGoToTheBiggestSnakeTheTrapperTheGluttonAndTheSurvivor() {
        TestArena arena =
                TestArena.start(Map.of("variation", "last-standing"), "alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 10, 10, 9, 10, 8, 10, 7, 10)
                .food(11, 10)
                .food(12, 10)
                .place("bob", Dir.UP, 30, 20, 30, 21, 30, 22, 30, 23)
                .ticks(2); // alice eats twice
        arena.place("bob", Dir.DOWN, 10, 9, 10, 8, 10, 7, 10, 6).ticks(1); // and bob runs into her

        assertThat(arena.game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(arena.game.lastTurn().points()).containsEntry("alice", 2 + 5 + 5 + 10);
        arena.game.finishGame();

        assertThat(arena.game.result().standings().getFirst().player().id()).isEqualTo("alice");
        assertThat(arena.game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Biggest snake", "alice", "Grew to 6 segments"),
                        tuple("Trapper", "alice", "1 snake ran into them"),
                        tuple("Glutton", "alice", "Ate 2 apples"),
                        tuple("Survivor", "alice", "No crashes"));
    }

    @Test
    void aFullGameReachesThePodium() {
        GameHarness game =
                TestArena.start(
                                Map.of("rounds", 2, "roundDuration", 60, "powerUps", true),
                                "alice",
                                "bob",
                                "carol")
                        .game;

        game.finishGame();

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(game.result().standings()).hasSize(3);
        assertThat(game.result().awards())
                .extracting(Award::title)
                .contains("Biggest snake", "Survivor");
    }

    // ------------------------------------------------------------------ helpers

    private static List<Integer> cells(TestArena arena, int... xy) {
        Integer[] cells = new Integer[xy.length / 2];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = arena.cell(xy[2 * i], xy[2 * i + 1]);
        }
        return List.of(cells);
    }

    private static List<Integer> range(int from, int to) {
        return IntStream.rangeClosed(from, to).boxed().toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("results");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> snakes(Map<String, Object> frame) {
        return (List<Map<String, Object>>) frame.get("snakes");
    }

    private static int maxPoints(TestArena arena) {
        return arena.game.lastTurn().points().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }
}
