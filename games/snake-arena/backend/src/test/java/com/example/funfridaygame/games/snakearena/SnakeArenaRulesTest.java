package com.example.funfridaygame.games.snakearena;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.games.snakearena.Board.PowerUp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for the arena's physics: steering, eating, crashes and credits, head-on and head
 * swaps, tails, respawns, power-ups and moving walls. Small arena (36×24), no random food.
 */
class SnakeArenaRulesTest {

    // ------------------------------------------------------------------ steering

    @Test
    void nothingMovesDuringTheStartDelayThenOneCellPerTick() {
        TestArena arena = TestArena.start("alice").calm();
        assertThat(arena.head("alice")).containsExactly(6, 12);

        arena.ticks(Simulation.START_DELAY_TICKS);
        assertThat(arena.head("alice")).containsExactly(6, 12);

        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(7, 12);
        arena.ticks(3);
        assertThat(arena.body("alice"))
                .containsExactly(List.of(10, 12), List.of(9, 12), List.of(8, 12), List.of(7, 12));
    }

    @Test
    void turnsQueueUpToTwoAndNeverReverse() {
        TestArena arena = TestArena.start("alice").calm().go();
        int changes = arena.game.changes();

        arena.turn("alice", "LEFT") // reverse: ignored
                .turn("alice", "RIGHT") // current heading: ignored
                .turn("alice", "UP")
                .turn("alice", "LEFT") // a quick double-turn
                .turn("alice", "DOWN"); // the queue is full: ignored
        assertThat(arena.game.changes()).as("input never triggers a snapshot").isEqualTo(changes);

        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(6, 11);
        assertThat(arena.snake("alice").dir()).isEqualTo(Dir.UP);
        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(5, 11);
        arena.ticks(1);
        assertThat(arena.head("alice"))
                .as("straight on once the queue is empty")
                .containsExactly(4, 11);
        assertThat(arena.frameSnake("alice").get("dir")).isEqualTo("LEFT");
    }

    @Test
    void aTurnAgainstTheQueuedTurnIsIgnored() {
        TestArena arena = TestArena.start("alice").calm().go();

        arena.turn("alice", "UP").turn("alice", "DOWN").ticks(2);

        assertThat(arena.head("alice")).containsExactly(6, 10);
    }

    @Test
    void aTurnWaitsForTheCellThePlayerSawTheirHeadIn() {
        TestArena arena = TestArena.start("alice").calm().go();
        long now = arena.sim().tick(); // the head is at 6,12, heading right

        // Browsers draw a little ahead: the player pressed while their head showed in the next
        // cell.
        arena.turn("alice", "UP", now + 1).ticks(1);
        assertThat(arena.head("alice")).containsExactly(7, 12);
        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(7, 11);
    }

    @Test
    void aLateTurnIsCarriedOutByTheNextMove() {
        TestArena arena = TestArena.start("alice").calm().go().ticks(3);
        assertThat(arena.head("alice")).containsExactly(9, 12);

        arena.turn("alice", "UP", arena.sim().tick() - 2).ticks(1);

        assertThat(arena.head("alice")).containsExactly(9, 11);
    }

    @Test
    void aTurnWaitsAtMostTwoTicks() {
        TestArena arena = TestArena.start("alice").calm().go();

        arena.turn("alice", "DOWN", arena.sim().tick() + 50).ticks(2);
        assertThat(arena.snake("alice").dir()).isEqualTo(Dir.RIGHT);
        arena.ticks(1);
        assertThat(arena.snake("alice").dir()).isEqualTo(Dir.DOWN);
    }

    @Test
    void rejectsUnknownActionsAndInvalidDirections() {
        TestArena arena = TestArena.start("alice", "bob");

        assertThat(arena.game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown");
        assertThat(arena.game.reject("alice", "turn", Map.of("dir", "NORTH")).getMessage())
                .contains("dir");
        assertThat(arena.game.reject("alice", "turn", Map.of()).getMessage()).contains("dir");
        assertThat(arena.game.reject("alice", "turn", Map.of("dir", "UP", "at", -3)).getMessage())
                .startsWith("at ");
    }

    // ------------------------------------------------------------------ food

    @Test
    void foodScoresAPointAndTheSnakeGrows() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.food(7, 12).ticks(1);

        Snake alice = arena.snake("alice");
        assertThat(alice.length()).isEqualTo(5);
        assertThat(alice.points()).isEqualTo(1);
        assertThat(alice.food()).isEqualTo(1);
        assertThat(arena.board().foodCount()).isZero();
        assertThat(arena.body("alice").getLast())
                .as("the tail stays put")
                .isEqualTo(List.of(3, 12));

        arena.ticks(1);
        assertThat(alice.length()).isEqualTo(5);
        assertThat(arena.body("alice").getLast()).isEqualTo(List.of(4, 12));
        assertThat(arena.frameSnake("alice")).containsEntry("points", 1).containsEntry("length", 5);
    }

    @Test
    void eatenFoodIsReplacedOnePiecePerTickUpToTwoPlusOneAndAHalfPerPlayer() {
        TestArena arena = TestArena.start("alice", "bob", "carol");
        assertThat(arena.board().foodCount()).isEqualTo(7);

        List<Integer> food = arena.board().food();
        arena.board().removeFood(food.get(0));
        arena.board().removeFood(food.get(1));
        arena.ticks(1);
        assertThat(arena.board().foodCount()).isEqualTo(6);
        arena.ticks(1);
        assertThat(arena.board().foodCount()).isEqualTo(7);
        arena.ticks(1);
        assertThat(arena.board().foodCount()).isEqualTo(7);
    }

    // ------------------------------------------------------------------ crashes

    @Test
    void hittingAWallCrashesAndEverySecondBodyCellBecomesFood() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.place("alice", Dir.RIGHT, 35, 5, 34, 5, 33, 5, 32, 5).ticks(1);

        Snake alice = arena.snake("alice");
        assertThat(alice.alive()).isFalse();
        assertThat(alice.crashes()).isEqualTo(1);
        assertThat(alice.respawnAt()).isEqualTo(11 + Simulation.RESPAWN_TICKS);
        assertThat(arena.board().food()).containsExactly(arena.cell(35, 5), arena.cell(33, 5));
        assertThat(arena.feed().getLast())
                .containsEntry("kind", "WALL")
                .containsEntry("a", "alice");
        assertThat(arena.frameSnake("alice"))
                .containsEntry("alive", false)
                .containsEntry("length", 0)
                .containsEntry("respawnAt", 41)
                .containsEntry("body", List.of());
    }

    @Test
    void aLongSnakeLeavesAtMostThirtyPiecesOfFood() {
        TestArena arena = TestArena.start("alice").calm().go();
        int[] xy = new int[2 * 34 * 3];
        int i = 0;
        for (int y = 0; y < 3; y++) { // a zig-zag of 102 cells, head at (34, 0)
            for (int k = 0; k < 34; k++) {
                xy[i++] = y == 1 ? 1 + k : 34 - k;
                xy[i++] = y;
            }
        }
        arena.place("alice", Dir.UP, xy).ticks(1);

        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.board().foodCount()).isBetween(25, Simulation.CRASH_FOOD_CAP);
    }

    @Test
    void bitingYourselfIsACrash() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.place("alice", Dir.LEFT, 5, 5, 6, 5, 6, 6, 5, 6, 4, 6).turn("alice", "DOWN").ticks(1);

        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.feed().getLast()).containsEntry("kind", "SELF");
    }

    @Test
    void runningIntoAnotherSnakeCreditsItsOwner() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 10, 10, 9, 10, 8, 10, 7, 10)
                .place("bob", Dir.DOWN, 8, 9, 8, 8, 8, 7, 8, 6)
                .ticks(1);

        assertThat(arena.snake("bob").alive()).isFalse();
        assertThat(arena.snake("alice").alive()).isTrue();
        assertThat(arena.snake("alice").points()).isEqualTo(Simulation.BODY_CREDIT);
        assertThat(arena.snake("alice").credits()).isEqualTo(1);
        assertThat(arena.feed().getLast())
                .containsEntry("kind", "BODY")
                .containsEntry("a", "alice")
                .containsEntry("b", "bob");
    }

    @Test
    void headOnCrashesBothWithoutCredit() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 10, 10, 9, 10, 8, 10, 7, 10)
                .place("bob", Dir.LEFT, 12, 10, 13, 10, 14, 10, 15, 10)
                .ticks(1);

        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.snake("bob").alive()).isFalse();
        assertThat(arena.snake("alice").points() + arena.snake("bob").points()).isZero();
        assertThat(arena.feed()).hasSize(1);
        assertThat(arena.feed().getFirst()).containsEntry("kind", "HEAD");
    }

    @Test
    void swappingHeadsCrashesBoth() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 10, 10, 9, 10, 8, 10, 7, 10)
                .place("bob", Dir.LEFT, 11, 10, 12, 10, 13, 10, 14, 10)
                .ticks(1);

        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.snake("bob").alive()).isFalse();
        assertThat(arena.snake("alice").credits() + arena.snake("bob").credits()).isZero();
        assertThat(arena.feed())
                .singleElement()
                .satisfies(e -> assertThat(e.get("kind")).isEqualTo("HEAD"));
    }

    @Test
    void chasingATailIsSafe() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        // alice circles in a 2×2 square, always entering the cell her tail leaves
        arena.place("alice", Dir.LEFT, 5, 5, 6, 5, 6, 6, 5, 6)
                .place("bob", Dir.RIGHT, 20, 10, 19, 10, 18, 10, 17, 10); // out of the way
        for (String dir : List.of("DOWN", "RIGHT", "UP", "LEFT", "DOWN")) {
            arena.turn("alice", dir).ticks(1);
            assertThat(arena.snake("alice").alive()).as("turning " + dir).isTrue();
        }
        assertThat(arena.head("alice")).containsExactly(5, 6);
    }

    @Test
    void followingAnotherSnakesTailIsSafe() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("bob", Dir.RIGHT, 12, 10, 11, 10, 10, 10, 9, 10)
                .place("alice", Dir.RIGHT, 8, 10, 7, 10, 6, 10, 5, 10)
                .ticks(3);

        assertThat(arena.snake("alice").alive()).isTrue();
        assertThat(arena.snake("bob").alive()).isTrue();
        assertThat(arena.head("alice")).containsExactly(11, 10);
    }

    @Test
    void aTailThatGrowsThisTickStaysPut() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("bob", Dir.RIGHT, 12, 10, 11, 10, 10, 10, 9, 10)
                .place("alice", Dir.RIGHT, 8, 10, 7, 10, 6, 10, 5, 10)
                .food(13, 10)
                .ticks(1);

        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.snake("bob").points()).isEqualTo(1 + Simulation.BODY_CREDIT);
    }

    // ------------------------------------------------------------------ respawn

    @Test
    void crashedSnakesRespawnAfterThreeSecondsAndKeepTheirPoints() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.food(7, 12).ticks(1); // tick 11: +1
        arena.place("alice", Dir.UP, 7, 0, 7, 1, 7, 2, 7, 3, 7, 4).ticks(1); // tick 12: crash
        Snake alice = arena.snake("alice");
        assertThat(alice.alive()).isFalse();

        arena.turn("alice", "UP"); // harmless while crashed: ignored without an error
        arena.ticks(Simulation.RESPAWN_TICKS - 1);
        assertThat(alice.alive()).isFalse();
        assertThat(arena.frameSnake("alice")).containsEntry("respawnAt", 42);

        arena.ticks(1);
        assertThat(alice.alive()).isTrue();
        assertThat(alice.length()).isEqualTo(Spawner.START_LENGTH);
        assertThat(alice.points()).isEqualTo(1);
        assertThat(arena.frameSnake("alice").get("respawnAt")).isNull();
        List<List<Integer>> spawned = arena.body("alice");
        Dir dir = alice.dir();
        for (int i = 1; i < spawned.size(); i++) {
            assertThat(spawned.get(i - 1).get(0) - spawned.get(i).get(0)).isEqualTo(dir.dx());
            assertThat(spawned.get(i - 1).get(1) - spawned.get(i).get(1)).isEqualTo(dir.dy());
        }
        for (List<Integer> cell : spawned) {
            assertThat(cell.get(0)).isBetween(1, 34);
            assertThat(cell.get(1)).isBetween(1, 22);
        }

        arena.ticks(1);
        assertThat(arena.body("alice").getFirst())
                .as("moves on the tick after it appeared")
                .isEqualTo(
                        List.of(
                                spawned.getFirst().get(0) + dir.dx(),
                                spawned.getFirst().get(1) + dir.dy()));
    }

    // ------------------------------------------------------------------ power-ups

    @Test
    void boostMovesTwoCellsPerTickForThreeSeconds() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.powerUp(7, 12, PowerType.BOOST).ticks(1); // tick 11: picked up

        Snake alice = arena.snake("alice");
        assertThat(alice.power()).isEqualTo(PowerType.BOOST);
        assertThat(arena.board().powerUps()).isEmpty();
        assertThat(arena.frameSnake("alice"))
                .containsEntry("power", "BOOST")
                .containsEntry("powerUntil", 41);

        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(9, 12);
        arena.ticks(1);
        assertThat(arena.body("alice"))
                .containsExactly(List.of(11, 12), List.of(10, 12), List.of(9, 12), List.of(8, 12));

        for (int lap = 0; lap < 3; lap++) { // keep away from the wall until tick 40
            arena.place("alice", Dir.RIGHT, 4, 12, 3, 12, 2, 12, 1, 12).ticks(lap < 2 ? 10 : 7);
        }
        assertThat(arena.sim().tick()).isEqualTo(40);
        assertThat(arena.head("alice")).containsExactly(18, 12);
        assertThat(alice.power()).isEqualTo(PowerType.BOOST);

        arena.ticks(1); // tick 41: the last boosted tick
        assertThat(arena.head("alice")).containsExactly(20, 12);
        assertThat(alice.power()).isNull();
        arena.ticks(1);
        assertThat(arena.head("alice")).containsExactly(21, 12);
    }

    @Test
    void ghostsPassThroughOtherSnakesAndTheyThroughTheGhost() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 6, 10, 5, 10, 4, 10, 3, 10)
                .place("bob", Dir.UP, 9, 7, 9, 8, 9, 9, 9, 10, 9, 11, 9, 12, 9, 13, 9, 14);
        arena.snake("alice").grant(PowerType.GHOST, 100);

        arena.ticks(3); // alice's head enters bob's body
        assertThat(arena.head("alice")).containsExactly(9, 10);
        assertThat(arena.snake("bob").body()).contains(arena.cell(9, 10));
        assertThat(arena.snake("alice").alive()).isTrue();
        assertThat(arena.snake("bob").alive()).isTrue();

        arena.place("alice", Dir.RIGHT, 12, 15, 11, 15, 10, 15, 9, 15, 8, 15, 7, 15, 6, 15, 5, 15)
                .place("bob", Dir.DOWN, 8, 13, 8, 12, 8, 11, 8, 10)
                .ticks(2); // bob's head enters the ghost's body
        assertThat(arena.head("bob")).containsExactly(8, 15);
        assertThat(arena.snake("bob").alive()).isTrue();
        assertThat(arena.snake("alice").points()).as("no credit for a ghost").isZero();
    }

    @Test
    void ghostsStillCrashHeadOnAndIntoWalls() {
        TestArena arena = TestArena.start("alice", "bob").calm().go();
        arena.place("alice", Dir.RIGHT, 20, 5, 19, 5, 18, 5, 17, 5)
                .place("bob", Dir.LEFT, 22, 5, 23, 5, 24, 5, 25, 5);
        arena.snake("alice").grant(PowerType.GHOST, 100);
        arena.snake("bob").grant(PowerType.GHOST, 100);
        arena.ticks(1);
        assertThat(arena.snake("alice").alive()).isFalse();
        assertThat(arena.snake("bob").alive()).isFalse();

        TestArena walls = TestArena.start("carol").calm().go();
        walls.place("carol", Dir.UP, 3, 0, 3, 1, 3, 2, 3, 3);
        walls.snake("carol").grant(PowerType.GHOST, 100);
        walls.ticks(1);
        assertThat(walls.snake("carol").alive()).isFalse();
    }

    @Test
    void magnetsPullNearbyFoodOneCellPerTick() {
        TestArena arena = TestArena.start("alice").calm().go();
        arena.place("alice", Dir.UP, 5, 12, 5, 13, 5, 14, 5, 15).food(9, 11).food(20, 20);
        arena.snake("alice").grant(PowerType.MAGNET, 100);

        arena.ticks(1); // alice → (5, 11); the near piece is 4 cells away
        assertThat(arena.board().food())
                .containsExactlyInAnyOrder(arena.cell(8, 11), arena.cell(20, 20));
        arena.ticks(1); // alice → (5, 10)
        assertThat(arena.board().food()).contains(arena.cell(7, 11), arena.cell(20, 20));
        arena.ticks(2);
        assertThat(arena.board().food()).as("far food never moves").contains(arena.cell(20, 20));
        assertThat(arena.board().food()).doesNotContain(arena.cell(9, 11), arena.cell(8, 11));
    }

    @Test
    void powerUpsAppearEveryTenToFifteenSecondsAndVanishAfterFifteen() {
        TestArena arena = TestArena.start(Map.of("powerUps", true), "alice").calm();
        long appeared = -1;
        for (int tick = 1; tick <= 150 && appeared < 0; tick++) {
            keepOnTheBottomRow(arena, tick);
            arena.ticks(1);
            if (!arena.board().powerUps().isEmpty()) {
                appeared = tick;
            }
        }
        assertThat(appeared).isBetween(100L, 150L);
        PowerUp first = arena.board().powerUps().iterator().next();
        assertThat(first.until()).isEqualTo(appeared + Simulation.POWER_UP_LIFETIME);
        assertThat(arena.board().x(first.cell())).isBetween(2, 33);
        assertThat(arena.board().y(first.cell())).isBetween(2, 21);
        assertThat((List<?>) arena.frame().get("powerUps")).hasSize(1);

        for (long tick = appeared + 1; tick < first.until(); tick++) {
            keepOnTheBottomRow(arena, tick);
            arena.ticks(1);
            assertThat(arena.board().powerUps()).hasSizeLessThanOrEqualTo(Simulation.MAX_POWER_UPS);
        }
        assertThat(arena.board().powerUpAt(first.cell())).isNotNull();
        arena.ticks(1);
        assertThat(arena.board().powerUpAt(first.cell())).isNull();
    }

    /** Power-ups never appear next to the walls, so a snake on the bottom row can't take one. */
    private static void keepOnTheBottomRow(TestArena arena, long tick) {
        if (tick % 20 == 1) {
            arena.place("alice", Dir.RIGHT, 6, 23, 5, 23, 4, 23, 3, 23);
        }
    }

    // ------------------------------------------------------------------ shrinking arena

    @Test
    void theWallsCloseInDuringTheLastThirtySeconds() {
        TestArena arena =
                TestArena.start(Map.of("variation", "shrinking", "roundDuration", 60), "alice")
                        .calm();
        assertThat(arena.game.view("alice").get("shrink"))
                .isEqualTo(Map.of("from", 300, "every", 30, "minWidth", 12, "minHeight", 8));

        arena.ticks(299);
        assertThat(arena.board().bounds()).isEqualTo(new Bounds(0, 0, 35, 23));
        arena.calm() // drop what earlier crashes left behind
                .place("alice", Dir.DOWN, 0, 10, 0, 9, 0, 8, 0, 7)
                .food(0, 20)
                .food(1, 20);

        arena.ticks(1); // tick 300
        assertThat(arena.board().bounds()).isEqualTo(new Bounds(1, 1, 34, 22));
        assertThat(arena.snake("alice").alive()).as("crushed by the wall").isFalse();
        assertThat(arena.feed().getLast()).containsEntry("kind", "WALL");
        assertThat(arena.board().food()).containsExactly(arena.cell(1, 20));
        assertThat(arena.frame().get("bounds"))
                .isEqualTo(Map.of("x0", 1, "y0", 1, "x1", 34, "y1", 22));

        arena.ticks(29);
        assertThat(arena.board().bounds()).isEqualTo(new Bounds(1, 1, 34, 22));
        arena.ticks(1);
        assertThat(arena.board().bounds()).isEqualTo(new Bounds(2, 2, 33, 21));

        arena.game.finishTurn();
        assertThat(arena.board().bounds())
                .as("ten moves; the height stops at 8")
                .isEqualTo(new Bounds(10, 8, 25, 15));
    }

    @Test
    void classicWallsNeverMove() {
        TestArena arena = TestArena.start(Map.of("roundDuration", 60), "alice").calm();
        assertThat(arena.game.view("alice").get("shrink")).isNull();
        arena.game.finishTurn();
        assertThat(arena.board().bounds()).isEqualTo(new Bounds(0, 0, 35, 23));
    }
}
