package com.example.funfridaygame.games.slidingpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Scenario tests: plays Sliding Puzzle through the real engine with virtual time. */
class SlidingPuzzleGameTest {

    private static GameHarness game(Map<String, ?> settings, String... players) {
        Map<String, Object> all = new HashMap<>(Map.of("rounds", 1, "size", 3));
        all.putAll(settings);
        return GameHarness.of(new SlidingPuzzleGame())
                .players(players)
                .settings(all)
                .seed(42)
                .start();
    }

    private static GameHarness game(String... players) {
        return game(Map.of(), players);
    }

    /** A player's view as the browser receives it (null fields are left out of the JSON). */
    private static Map<String, Object> view(GameHarness game, String player) {
        return sent(game.view(player));
    }

    private static Map<String, Object> spectatorView(GameHarness game) {
        return sent(game.spectatorView());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sent(Map<String, Object> json) {
        Map<String, Object> present = new LinkedHashMap<>();
        json.forEach(
                (key, value) -> {
                    if (value instanceof List<?> list) {
                        present.put(
                                key,
                                list.stream()
                                        .map(
                                                item ->
                                                        item instanceof Map<?, ?> map
                                                                ? sent((Map<String, Object>) map)
                                                                : item)
                                        .toList());
                    } else if (value != null) {
                        present.put(key, value);
                    }
                });
        return present;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> board(GameHarness game, String player) {
        return (List<Integer>) view(game, player).get("board");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> status(Map<String, Object> view, String player) {
        return ((List<Map<String, Object>>) view.get("players"))
                .stream().filter(s -> player.equals(s.get("playerId"))).findFirst().orElseThrow();
    }

    private static int scrambleDistance(GameHarness game) {
        return (Integer) game.debugState().get("scrambleDistance");
    }

    private static GameHarness move(GameHarness game, String player, int tile) {
        return game.act(player, "move", Map.of("tile", tile));
    }

    /** Plays a shortest solution of the player's current 3×3 board; returns its length. */
    private static int solve(GameHarness game, String player) {
        List<Integer> tiles = PuzzleSolver.solve(board(game, player), 3);
        tiles.forEach(tile -> move(game, player, tile));
        return tiles.size();
    }

    /** A tile next to the gap on the player's board. */
    private static int neighbourOfGap(GameHarness game, String player) {
        Board board = toBoard(board(game, player));
        return board.tileAt(board.gapNeighbours()[0]);
    }

    /** Slides a tile next to the gap back and forth: two moves each, same board afterwards. */
    private static void wiggle(GameHarness game, String player, int times) {
        for (int i = 0; i < times; i++) {
            int tile = neighbourOfGap(game, player);
            move(game, player, tile);
            move(game, player, tile);
        }
    }

    private static Board toBoard(List<Integer> cells) {
        int size = (int) Math.round(Math.sqrt(cells.size()));
        return Board.of(size, cells.stream().mapToInt(Integer::intValue).toArray());
    }

    @Test
    void everyoneGetsTheSameHardScramble() {
        GameHarness game = game(Map.of("size", 4), "alice", "bob", "carol");

        Map<String, Object> view = view(game, "alice");
        Board start = toBoard(board(game, "alice"));
        assertThat(view)
                .containsEntry("size", 4)
                .containsEntry("start", start.cells())
                .containsEntry("moves", 0)
                .containsEntry("finished", false);
        assertThat(board(game, "bob")).isEqualTo(start.cells());
        assertThat(board(game, "carol")).isEqualTo(start.cells());
        assertThat(start.isSolved()).isFalse();
        assertThat(start.manhattanDistance()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void aPushSlidesEveryTileBetweenItAndTheGapAndCountsEachTile() {
        GameHarness game = game(Map.of("size", 4), "alice", "bob");
        Board expected = toBoard(board(game, "alice"));
        int gap = expected.gapIndex();
        // The farthest cell of the gap's row is always at least two cells away on a 4×4 board.
        int farCell = gap % 4 < 2 ? gap - gap % 4 + 3 : gap - gap % 4;
        int tile = expected.tileAt(farCell);
        int length = expected.slide(tile);

        move(game, "alice", tile);

        assertThat(length).isGreaterThanOrEqualTo(2);
        assertThat(board(game, "alice")).isEqualTo(expected.cells());
        assertThat(view(game, "alice")).containsEntry("moves", length);
        assertThat(status(view(game, "bob"), "alice")).containsEntry("moves", length);
        assertThat(board(game, "bob"))
                .as("bob's copy is untouched")
                .isEqualTo(view(game, "bob").get("start"));
    }

    @Test
    void solversGetRankAndTimeAndTheRoundEndsWhenEveryoneSolved() {
        GameHarness game = game("alice", "bob");
        int distance = scrambleDistance(game);

        game.advanceSeconds(10);
        int aliceMoves = solve(game, "alice");
        assertThat(view(game, "alice"))
                .containsEntry("finished", true)
                .containsEntry("rank", 1)
                .containsEntry("timeMs", 10_000L);
        assertThat(status(view(game, "bob"), "alice"))
                .containsEntry("finished", true)
                .containsEntry("rank", 1)
                .containsEntry("progress", 100);
        assertThat(game.phase()).as("bob is still solving").isEqualTo(RunPhase.PLAYING);

        game.advanceSeconds(5);
        wiggle(game, "bob", 3);
        int bobMoves = solve(game, "bob") + 6;

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("alice solved it first in 10.0 s!");
        assertThat(game.lastTurn().points())
                .containsEntry("alice", 100 + Scoring.efficiencyBonus(distance, aliceMoves))
                .containsEntry("bob", 80 + Scoring.efficiencyBonus(distance, bobMoves));
        assertThat(status(view(game, "alice"), "bob"))
                .containsEntry("rank", 2)
                .containsEntry("timeMs", 15_000L)
                .containsEntry("moves", bobMoves);
    }

    @Test
    void theDeadlineEndsTheRoundAndUnsolvedBoardsScoreTheirProgress() {
        GameHarness game = game(Map.of("size", 4), "alice", "bob");
        move(game, "alice", neighbourOfGap(game, "alice"));
        Map<String, Object> alice = status(view(game, "alice"), "alice");

        game.advanceSeconds(149.9);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.1);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        int points = Scoring.progressPoints((Integer) alice.get("inPlace"), 15);
        assertThat(game.lastTurn().points().getOrDefault("alice", 0)).isEqualTo(points);
        assertThat(game.lastTurn().points()).as("bob never moved").doesNotContainKey("bob");
        assertThat(game.lastTurn().headline())
                .isEqualTo(
                        "Nobody solved it — alice got closest ("
                                + alice.get("progress")
                                + "% of tiles in place)");
    }

    @ParameterizedTest
    @CsvSource({"3, 0, 60", "4, 0, 150", "5, 0, 300", "3, 180, 180", "5, 120, 120"})
    void theTimeLimitDependsOnTheBoardSize(int size, int timeLimit, int seconds) {
        GameHarness game = game(Map.of("size", size, "timeLimit", timeLimit), "alice", "bob");

        assertThat(game.remainingMillis()).isEqualTo(seconds * 1000L);
        game.advanceSeconds(seconds - 1);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(1);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
    }

    @Test
    void rejectsInvalidMoves() {
        GameHarness game = game("alice", "bob");
        Board board = toBoard(board(game, "alice"));
        int gap = board.gapIndex();
        int outOfLine = board.tileAt((gap / 3 + 1) % 3 * 3 + (gap % 3 + 1) % 3);

        assertThat(game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown");
        assertThat(game.reject("alice", "move", Map.of()).getMessage()).contains("tile");
        assertThat(game.reject("alice", "move", Map.of("tile", 0)).getMessage()).contains("tile");
        assertThat(game.reject("alice", "move", Map.of("tile", 9)).getMessage())
                .contains("no tile 9");
        assertThat(game.reject("alice", "move", Map.of("tile", outOfLine)).getMessage())
                .contains("gap's row or column");
        assertThat(view(game, "alice")).containsEntry("moves", 0);

        solve(game, "alice");
        int next = neighbourOfGap(game, "alice");
        assertThat(game.reject("alice", "move", Map.of("tile", next)).getMessage())
                .contains("already solved");

        game.finishTurn();
        int bobs = neighbourOfGap(game, "bob");
        assertThat(game.reject("bob", "move", Map.of("tile", bobs)).getMessage())
                .contains("Wait for the next turn");
    }

    @Test
    void otherPlayersBoardsStayHiddenFromPlayersAndSpectators() {
        GameHarness game = game("alice", "bob");
        move(game, "alice", neighbourOfGap(game, "alice"));
        List<Integer> aliceBoard = board(game, "alice");

        Map<String, Object> bob = view(game, "bob");
        assertThat(bob.get("board")).isEqualTo(bob.get("start")).isNotEqualTo(aliceBoard);
        assertThat(status(bob, "alice"))
                .containsOnlyKeys("playerId", "inPlace", "progress", "moves", "finished")
                .containsEntry("moves", 1);

        Map<String, Object> spectator = spectatorView(game);
        assertThat(spectator).doesNotContainKeys("board", "moves", "finished", "rank", "timeMs");
        assertThat(spectator.get("start")).isEqualTo(bob.get("start"));
        assertThat(spectator.toString()).doesNotContain(aliceBoard.toString());

        game.finishTurn();
        assertThat(status(view(game, "bob"), "alice")).doesNotContainKey("board");
        assertThat(spectatorView(game)).doesNotContainKey("board");
    }

    @Test
    void theSecondRoundStartsWithAFreshPuzzleAndNoResultsOfTheFirst() {
        GameHarness game = game(Map.of("rounds", 2), "alice", "bob");
        Object firstStart = view(game, "alice").get("start");
        solve(game, "alice");
        game.finishTurn();
        assertThat(view(game, "bob")).containsEntry("revealed", true);

        game.nextTurn();

        assertThat(game.round()).isEqualTo(2);
        Map<String, Object> alice = view(game, "alice");
        assertThat(alice)
                .containsEntry("round", 2)
                .containsEntry("revealed", false)
                .containsEntry("finished", false)
                .containsEntry("moves", 0)
                .doesNotContainKeys("rank", "timeMs");
        assertThat(alice.get("board")).isEqualTo(alice.get("start")).isNotEqualTo(firstStart);
        assertThat(status(view(game, "bob"), "alice"))
                .containsEntry("finished", false)
                .containsEntry("moves", 0)
                .doesNotContainKeys("rank", "timeMs");
    }

    @Test
    void disconnectedPlayersDoNotBlockTheRound() {
        GameHarness game = game("alice", "bob", "carol");
        move(game, "bob", neighbourOfGap(game, "bob"));
        solve(game, "alice");
        game.disconnect("carol");
        assertThat(game.phase()).as("bob is connected and solving").isEqualTo(RunPhase.PLAYING);

        game.disconnect("bob");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsKey("alice").doesNotContainKey("carol");
    }

    @Test
    void aPlayerWhoLeavesMidRoundDisappearsAndADepartedWinnerMovesEveryoneUp() {
        GameHarness game = game("alice", "bob", "carol");
        solve(game, "alice");
        game.leave("alice");

        assertThat(view(game, "bob").get("players").toString()).doesNotContain("alice");
        game.advanceSeconds(3);
        int bobMoves = solve(game, "bob");
        assertThat(view(game, "bob")).as("alice's win left with her").containsEntry("rank", 1);
        assertThat(game.phase()).as("carol is still solving").isEqualTo(RunPhase.PLAYING);

        game.disconnect("carol");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        int distance = scrambleDistance(game);
        assertThat(game.lastTurn().points())
                .doesNotContainKey("alice")
                .containsEntry("bob", 100 + Scoring.efficiencyBonus(distance, bobMoves));
    }

    @Test
    void playsAFullGameToThePodiumWithAwards() {
        GameHarness game = game(Map.of("rounds", 2), "alice", "bob");
        for (int round = 1; round <= 2; round++) {
            game.advanceSeconds(8);
            solve(game, "alice");
            game.advanceSeconds(4);
            wiggle(game, "bob", 10);
            solve(game, "bob");
            assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
            game.nextTurn();
        }

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
        assertThat(game.result().awards())
                .extracting(Award::title, Award::playerId)
                .containsExactly(
                        tuple("Speed solver", "alice"),
                        tuple("Efficient", "alice"),
                        tuple("Persistent", "bob"));
        assertThat(game.result().awards().getFirst().detail()).isEqualTo("8.0 s");
    }

    @Test
    void aFiveByFiveBoardNeedsAtLeastTwoMinutes() {
        assertThatThrownBy(() -> game(Map.of("size", 5, "timeLimit", 60), "alice", "bob"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("at least 2 minutes");
        assertThat(game(Map.of("size", 5, "timeLimit", 120), "alice", "bob").phase())
                .isEqualTo(RunPhase.PLAYING);
    }
}
