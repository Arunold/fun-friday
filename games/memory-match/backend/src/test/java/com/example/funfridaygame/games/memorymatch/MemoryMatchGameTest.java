package com.example.funfridaygame.games.memorymatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays Memory Match through the real engine with virtual time. */
class MemoryMatchGameTest {

    private static final Map<String, Object> SMALL =
            Map.of("rounds", 1, "board", "4x4", "moveTime", 10);

    private static GameHarness game(Map<String, ?> settings, String... players) {
        return GameHarness.of(new MemoryMatchGame())
                .players(players)
                .settings(settings)
                .seed(42)
                .start();
    }

    // ------------------------------------------------------------------ moves

    @Test
    void aPairStaysUpInTheFindersNameAndTheyGoAgain() {
        GameHarness game = game(SMALL, "alice", "bob");
        assertThat(mover(game)).isEqualTo("alice");

        List<Integer> pair = matchAPair(game, "alice");

        for (int index : pair) {
            assertThat(card(game, index))
                    .containsEntry("state", "MATCHED")
                    .containsEntry("matchedBy", "alice")
                    .containsEntry("emoji", faces(game).get(index));
        }
        assertThat(mover(game)).isEqualTo("alice");
        assertThat(game.spectatorView()).containsEntry("pairsLeft", 7).containsEntry("streak", 1);
        assertThat(last(game))
                .containsEntry("kind", "MATCH")
                .containsEntry("playerId", "alice")
                .containsEntry("points", 100)
                .containsEntry("streak", 1);

        matchAPair(game, "alice");

        assertThat(last(game)).containsEntry("points", 150).containsEntry("streak", 2);
        assertThat(progress(game, "alice")).containsEntry("pairs", 2).containsEntry("points", 250);
        assertThat(progress(game, "bob")).containsEntry("pairs", 0).containsEntry("points", 0);

        missTwoCards(game, "alice");
        assertThat(game.spectatorView())
                .containsEntry("streak", 0)
                .containsEntry("resolving", true);
    }

    @Test
    void aMissStaysVisibleForAMomentThenTheNextPlayerMoves() {
        GameHarness game = game(SMALL, "alice", "bob");
        List<Integer> missed = missTwoCards(game, "alice");

        Map<String, Object> view = game.view("bob");
        assertThat(view.get("resolving")).isEqualTo(true);
        assertThat(view.get("moveDeadline")).isNull();
        for (int index : missed) {
            assertThat(cards(view).get(index))
                    .containsEntry("state", "UP")
                    .containsEntry("emoji", faces(game).get(index));
        }
        assertThat(last(game))
                .containsEntry("kind", "MISS")
                .containsEntry("playerId", "alice")
                .containsEntry("cards", missed);
        int third = pairs(game).get(2).getFirst();
        assertThat(rejectFlip(game, "alice", third)).contains("flip back");
        assertThat(rejectFlip(game, "bob", third)).contains("alice's turn");

        game.advance(MemoryMatchLogic.FLIP_BACK);

        view = game.view("bob");
        assertThat(view.get("activePlayerId")).isEqualTo("bob");
        assertThat(view.get("resolving")).isEqualTo(false);
        assertThat(((Number) view.get("moveDeadline")).longValue()).isEqualTo(game.now() + 10_000);
        for (int index : missed) {
            assertThat(cards(view).get(index).get("state")).isEqualTo("HIDDEN");
            assertThat(cards(view).get(index).get("emoji")).isNull();
        }
        assertThat(progress(game, "alice")).containsEntry("pairs", 0);
    }

    @Test
    void rejectsFlipsThatBreakTheRulesWithoutChangingAnything() {
        GameHarness game = game(SMALL, "alice", "bob");
        List<Integer> pair = pairs(game).getFirst();

        assertThat(game.reject("alice", "peek", Map.of()).getMessage()).contains("Unknown action");
        assertThat(game.reject("alice", "flip", Map.of()).getMessage()).contains("index");
        assertThat(rejectFlip(game, "alice", -1)).contains("index");
        assertThat(rejectFlip(game, "alice", 16)).contains("no card 16");
        assertThat(rejectFlip(game, "bob", pair.get(0))).isEqualTo("It's alice's turn");

        flip(game, "alice", pair.get(0));
        assertThat(rejectFlip(game, "alice", pair.get(0))).contains("already face up");
        assertThat(rejectFlip(game, "bob", pair.get(1))).contains("alice's turn");
        flip(game, "alice", pair.get(1));
        assertThat(rejectFlip(game, "alice", pair.get(0))).contains("already been found");

        assertThat(game.spectatorView())
                .containsEntry("pairsLeft", 7)
                .containsEntry("activePlayerId", "alice");
        assertThat(progress(game, "bob")).containsEntry("pairs", 0);
    }

    @Test
    void aMoveThatIsNotFinishedInTimeIsForfeited() {
        GameHarness game = game(SMALL, "alice", "bob");
        int card = pairs(game).getFirst().getFirst();
        flip(game, "alice", card);

        game.advanceSeconds(9.9);
        assertThat(mover(game)).isEqualTo("alice");
        game.advanceSeconds(0.1);

        assertThat(mover(game)).isEqualTo("bob");
        assertThat(card(game, card)).containsEntry("state", "HIDDEN");
        assertThat(last(game))
                .containsEntry("kind", "TIMEOUT")
                .containsEntry("playerId", "alice")
                .containsEntry("cards", List.of(card));

        game.advanceSeconds(10);
        assertThat(mover(game)).as("bob didn't flip at all").isEqualTo("alice");
        assertThat(last(game)).containsEntry("kind", "TIMEOUT").containsEntry("cards", List.of());
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
    }

    @Test
    void everyPairStartsAFreshMoveTimer() {
        GameHarness game = game(SMALL, "alice", "bob");
        game.advanceSeconds(8);
        matchAPair(game, "alice");

        game.advanceSeconds(9);
        assertThat(mover(game)).isEqualTo("alice");
        game.advanceSeconds(1);
        assertThat(mover(game)).isEqualTo("bob");
    }

    // ------------------------------------------------------------------ secrets

    @Test
    void faceDownCardsNeverCarryTheirEmojiForPlayersOrSpectators() {
        GameHarness game = game(SMALL, "alice", "bob");
        assertNoHiddenFaceIsSent(game);

        List<Integer> pair = pairs(game).getFirst();
        flip(game, "alice", pair.get(0));
        assertNoHiddenFaceIsSent(game);
        flip(game, "alice", pair.get(1));
        assertNoHiddenFaceIsSent(game);

        missTwoCards(game, "alice");
        assertNoHiddenFaceIsSent(game);
        game.advance(MemoryMatchLogic.FLIP_BACK);
        assertNoHiddenFaceIsSent(game);

        assertThat(game.spectatorView()).isEqualTo(game.view("alice")).isEqualTo(game.view("bob"));
    }

    @Test
    void roundTwoStartsWithANewFaceDownBoardAndTheNextStarter() {
        GameHarness game = game(Map.of("rounds", 2, "board", "4x4"), "alice", "bob");
        List<String> firstBoard = faces(game);
        clearBoard(game);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.spectatorView().get("revealed")).isEqualTo(true);

        game.nextTurn();

        assertThat(game.round()).isEqualTo(2);
        Map<String, Object> view = game.view("alice");
        assertThat(view.get("revealed")).isEqualTo(false);
        assertThat(view.get("last")).isNull();
        assertThat(view.get("activePlayerId")).isEqualTo("bob");
        assertThat(cards(view))
                .allSatisfy(
                        card -> {
                            assertThat(card.get("state")).isEqualTo("HIDDEN");
                            assertThat(card.get("emoji")).isNull();
                        });
        assertThat(progress(game, "alice")).containsEntry("pairs", 0).containsEntry("points", 0);
        assertThat(faces(game)).isNotEqualTo(firstBoard);
        assertNoHiddenFaceIsSent(game);
    }

    // ------------------------------------------------------------------ round end & scoring

    @Test
    void clearingTheBoardEndsTheRoundWithStreakAndMostPairsBonuses() {
        GameHarness game = game(SMALL, "alice", "bob");
        for (int i = 0; i < 5; i++) {
            matchAPair(game, "alice");
        }
        missTwoCards(game, "alice");
        game.advance(MemoryMatchLogic.FLIP_BACK);
        for (int i = 0; i < 3; i++) {
            matchAPair(game, "bob");
        }

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline())
                .isEqualTo("Board cleared! alice found the most pairs (5)");
        // alice: 100 + 4 × 150 + 100 most pairs; bob: 100 + 2 × 150
        assertThat(game.lastTurn().points()).containsEntry("alice", 800).containsEntry("bob", 400);
        Map<String, Object> view = game.view("bob");
        assertThat(view.get("revealed")).isEqualTo(true);
        assertThat(view.get("activePlayerId")).isNull();
        assertThat(view.get("moveDeadline")).isNull();
        assertThat(progress(game, "alice")).containsEntry("mostPairs", true);
        assertThat(progress(game, "bob")).containsEntry("mostPairs", false);
    }

    @Test
    void playersTiedOnPairsShareTheRoundBonus() {
        GameHarness game = game(SMALL, "alice", "bob");
        for (int i = 0; i < 4; i++) {
            matchAPair(game, "alice");
        }
        missTwoCards(game, "alice");
        game.advance(MemoryMatchLogic.FLIP_BACK);
        for (int i = 0; i < 4; i++) {
            matchAPair(game, "bob");
        }

        assertThat(game.lastTurn().headline())
                .isEqualTo("Board cleared! alice and bob tie with 4 pairs each");
        assertThat(game.lastTurn().points()).containsEntry("alice", 650).containsEntry("bob", 650);
    }

    @Test
    void theRoundDeadlineIsASafetyNetThatRevealsTheRestOfTheBoard() {
        GameHarness game = game(SMALL, "alice", "bob");
        assertThat(game.remainingMillis()).as("8 pairs × 20 s").isEqualTo(160_000);
        matchAPair(game, "alice");

        game.advanceSeconds(160);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline())
                .isEqualTo("Time's up! alice found the most pairs (1)");
        assertThat(game.lastTurn().points()).containsOnly(Map.entry("alice", 200));
        assertThat(cards(game.spectatorView()))
                .allSatisfy(card -> assertThat(card.get("emoji")).isNotNull())
                .filteredOn(card -> "UP".equals(card.get("state")))
                .hasSize(14);
    }

    @Test
    void biggerBoardsAndSlowerMovesGetMoreRoundTime() {
        assertThat(game(Map.of("board", "6x6", "moveTime", 15), "alice", "bob").remainingMillis())
                .as("18 pairs × 30 s")
                .isEqualTo(540_000);
        assertThat(game(Map.of("board", "4x6", "moveTime", 5), "alice", "bob").remainingMillis())
                .as("12 pairs × 20 s")
                .isEqualTo(240_000);
    }

    // ------------------------------------------------------------------ roster changes

    @Test
    void aDisconnectedPlayerIsSkippedAtOnceAndRejoinsTheRotation() {
        GameHarness game = game(SMALL, "alice", "bob", "carol");
        int card = pairs(game).getFirst().getFirst();
        flip(game, "alice", card);

        game.disconnect("alice");

        assertThat(mover(game)).isEqualTo("bob");
        assertThat(card(game, card)).containsEntry("state", "HIDDEN");
        assertThat(last(game)).containsEntry("kind", "AWAY").containsEntry("playerId", "alice");

        missAndFlipBack(game, "bob");
        assertThat(mover(game)).isEqualTo("carol");
        missAndFlipBack(game, "carol");
        assertThat(mover(game)).as("alice is still away").isEqualTo("bob");

        game.reconnect("alice");
        missAndFlipBack(game, "bob");
        missAndFlipBack(game, "carol");
        assertThat(mover(game)).as("alice is back in the rotation").isEqualTo("alice");
    }

    @Test
    void aPlayerWhoLeavesIsSkippedAndDropsOutOfTheOrder() {
        GameHarness game = game(SMALL, "alice", "bob", "carol");
        matchAPair(game, "alice");

        game.leave("alice");

        assertThat(mover(game)).isEqualTo("bob");
        assertThat(players(game.spectatorView()))
                .extracting(p -> p.get("playerId"))
                .containsExactly("bob", "carol");
        clearBoard(game);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).doesNotContainKey("alice").containsKey("bob");
    }

    @Test
    void theLastConnectedPlayerKeepsPlayingAndTheRoundEndsWhenNobodyIsLeft() {
        GameHarness game = game(SMALL, "alice", "bob");
        game.disconnect("bob");
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);

        missAndFlipBack(game, "alice");
        assertThat(mover(game)).as("nobody else is connected").isEqualTo("alice");

        game.disconnect("alice");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("Round over! Nobody found a pair.");
    }

    // ------------------------------------------------------------------ game flow

    @Test
    void theStartingPlayerRotatesEveryRound() {
        GameHarness game = game(Map.of("rounds", 3, "board", "4x4"), "alice", "bob", "carol");
        assertThat(order(game)).containsExactly("alice", "bob", "carol");

        game.finishTurn().nextTurn();
        assertThat(game.round()).isEqualTo(2);
        assertThat(mover(game)).isEqualTo("bob");
        assertThat(order(game)).containsExactly("bob", "carol", "alice");

        game.finishTurn().nextTurn();
        assertThat(game.round()).isEqualTo(3);
        assertThat(mover(game)).isEqualTo("carol");
    }

    @Test
    void aNewBoardStartsWithTheFirstConnectedPlayerInTheRotatedOrder() {
        GameHarness game = game(Map.of("rounds", 2, "board", "4x4"), "alice", "bob", "carol");
        game.finishTurn();
        game.disconnect("bob");

        game.nextTurn();

        assertThat(game.round()).isEqualTo(2);
        assertThat(order(game)).containsExactly("bob", "carol", "alice");
        assertThat(mover(game)).as("bob would start but is away").isEqualTo("carol");
    }

    @Test
    void awardsTheLongestStreakTheMostPairsAndTheLuckiestFinds() {
        GameHarness game = game(SMALL, "alice", "bob");
        for (int i = 0; i < 3; i++) {
            matchAPair(game, "alice"); // never-seen cards: lucky
        }
        List<Integer> missed = missTwoCards(game, "alice");
        game.advance(MemoryMatchLogic.FLIP_BACK);
        for (int seen : missed) {
            int twin =
                    pairs(game).stream()
                            .filter(p -> p.contains(seen))
                            .findFirst()
                            .orElseThrow()
                            .stream()
                            .filter(i -> i != seen)
                            .findFirst()
                            .orElseThrow();
            flip(game, "bob", twin);
            flip(game, "bob", seen); // remembered, not lucky
        }
        for (int i = 0; i < 3; i++) {
            matchAPair(game, "bob");
        }
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);

        game.finishGame();

        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("bob");
        assertThat(game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Elephant memory", "bob", "5 pairs in a row"),
                        tuple("Pair collector", "bob", "5 pairs"),
                        tuple("Lucky first try", "alice", "3 lucky pairs"));
    }

    @Test
    void usesTheDefaultSettings() {
        GameHarness game =
                GameHarness.of(new MemoryMatchGame()).players("alice", "bob").seed(1).start();

        Map<String, Object> view = game.view("alice");
        assertThat(view)
                .containsEntry("rows", 4)
                .containsEntry("cols", 6)
                .containsEntry("pairsTotal", 12)
                .containsEntry("moveTimeMs", 10_000L);
        assertThat(cards(view)).hasSize(24);
        assertThat(game.run().totalRounds()).isEqualTo(1);
        assertThat(game.remainingMillis()).isEqualTo(240_000);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static List<List<Integer>> pairs(GameHarness game) {
        return (List<List<Integer>>) game.debugState().get("pairs");
    }

    @SuppressWarnings("unchecked")
    private static List<String> faces(GameHarness game) {
        return (List<String>) game.debugState().get("faces");
    }

    private static String mover(GameHarness game) {
        return (String) game.spectatorView().get("activePlayerId");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cards(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("cards");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("players");
    }

    private static List<Object> order(GameHarness game) {
        return players(game.spectatorView()).stream().map(p -> p.get("playerId")).toList();
    }

    private static Map<String, Object> card(GameHarness game, int index) {
        return cards(game.spectatorView()).get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> last(GameHarness game) {
        return (Map<String, Object>) game.spectatorView().get("last");
    }

    private static Map<String, Object> progress(GameHarness game, String playerId) {
        return players(game.spectatorView()).stream()
                .filter(p -> playerId.equals(p.get("playerId")))
                .findFirst()
                .orElseThrow();
    }

    private static void flip(GameHarness game, String player, int index) {
        game.act(player, "flip", Map.of("index", index));
    }

    private static String rejectFlip(GameHarness game, String player, int index) {
        return game.reject(player, "flip", Map.of("index", index)).getMessage();
    }

    /** The player flips both cards of a pair nobody has found yet. */
    private static List<Integer> matchAPair(GameHarness game, String player) {
        List<Integer> pair = pairs(game).getFirst();
        flip(game, player, pair.get(0));
        flip(game, player, pair.get(1));
        return pair;
    }

    /** The player flips two cards of different pairs. */
    private static List<Integer> missTwoCards(GameHarness game, String player) {
        List<List<Integer>> pairs = pairs(game);
        List<Integer> cards = List.of(pairs.get(0).get(0), pairs.get(1).get(0));
        flip(game, player, cards.get(0));
        flip(game, player, cards.get(1));
        return cards;
    }

    private static void missAndFlipBack(GameHarness game, String player) {
        missTwoCards(game, player);
        game.advance(MemoryMatchLogic.FLIP_BACK);
    }

    /** Whoever moves finds pairs until the board is cleared. */
    private static void clearBoard(GameHarness game) {
        while (game.phase() == RunPhase.PLAYING) {
            matchAPair(game, mover(game));
        }
    }

    /**
     * Every face-down card comes without emoji, and the face of a pair whose cards are both face
     * down appears nowhere in any view.
     */
    private static void assertNoHiddenFaceIsSent(GameHarness game) {
        List<String> faces = faces(game);
        List<List<Integer>> pairs = pairs(game);
        for (Map<String, Object> view :
                List.of(game.view("alice"), game.view("bob"), game.spectatorView())) {
            List<Map<String, Object>> cards = cards(view);
            for (Map<String, Object> card : cards) {
                assertThat(card.get("emoji") == null)
                        .as("card %s", card)
                        .isEqualTo("HIDDEN".equals(card.get("state")));
            }
            for (List<Integer> pair : pairs) {
                if (pair.stream().allMatch(i -> "HIDDEN".equals(cards.get(i).get("state")))) {
                    assertThat(view.toString()).doesNotContain(faces.get(pair.getFirst()));
                }
            }
        }
    }
}
