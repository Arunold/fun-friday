package com.example.funfridaygame.games.reactionshowdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.Entry;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.SignalResult;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.TableRow;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for the flow of Reaction Showdown: signals, scoring, early ends, rounds, leaving
 * players and awards. Plays through the real engine with virtual time.
 */
class ReactionShowdownGameTest {

    private final Play play = new Play();

    @Test
    void theFastestTapWinsAndTheSignalEndsOnceEveryoneTapped() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob", "carol");
        play.untilGo();

        play.react("alice", 180).react("bob", 250);
        assertThat(play.state()).as("carol hasn't tapped yet").isEqualTo("GO");
        play.react("carol", 320);
        assertThat(play.state())
                .as("everyone tapped: no waiting for the window (BUG-06)")
                .isEqualTo("RESULT");

        SignalResult result = play.view("alice").results().getFirst();
        assertThat(result.fastestPlayerId()).isEqualTo("alice");
        assertThat(result.fastestMs()).isEqualTo(180);
        assertThat(result.entries())
                .extracting(Entry::playerId)
                .containsExactly("alice", "bob", "carol");
        assertThat(result.entries()).extracting(Entry::points).containsExactly(100, 80, 65);
        assertThat(result.entries()).extracting(Entry::rank).containsExactly(1, 2, 3);
        assertThat(play.view("bob").me().ms()).isEqualTo(250);
        assertThat(play.view("bob").me().roundPoints()).isEqualTo(80);
    }

    @Test
    void equalTimesShareARankAndItsPoints() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob", "carol");
        play.untilGo();

        play.react("alice", 200).react("bob", 200).react("carol", 300);

        SignalResult result = play.view("carol").results().getFirst();
        assertThat(result.entries()).extracting(Entry::rank).containsExactly(1, 1, 3);
        assertThat(result.entries()).extracting(Entry::points).containsExactly(100, 100, 65);
    }

    @Test
    void rankPointsDecreaseDownToAMinimum() {
        assertThat(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15))
                .extracting(SignalScoring::pointsForRank)
                .containsExactly(100, 80, 65, 50, 40, 30, 25, 20, 15, 10, 10);
    }

    @Test
    void theGoWindowClosesAfterTwoSecondsAndNoTapIsAMiss() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        play.untilGo();
        play.react("alice", 230);

        play.advance(ReactionShowdownLogic.GO_WINDOW_MS - 300);
        assertThat(play.state()).isEqualTo("GO");
        play.advance(300);
        assertThat(play.state()).isEqualTo("RESULT");

        Entry bob = play.entry(play.view("alice").results().getFirst(), "bob");
        assertThat(bob.ms()).isNull();
        assertThat(bob.falseStart()).isFalse();
        assertThat(bob.points()).isZero();
    }

    @Test
    void tappingWhileWaitingIsAFalseStart() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        assertThat(play.state()).isEqualTo("WAITING");

        play.jump("alice");
        assertThat(play.view("alice").me().falseStart()).isTrue();
        assertThat(play.view("bob").players())
                .filteredOn(p -> p.playerId().equals("alice"))
                .singleElement()
                .satisfies(p -> assertThat(p.falseStart()).isTrue());
        assertThat(play.state()).as("bob still gets his GO").isEqualTo("WAITING");

        play.untilGo();
        play.react("bob", 300);
        assertThat(play.state()).isEqualTo("RESULT");
        SignalResult result = play.view("bob").results().getFirst();
        assertThat(play.entry(result, "alice").points())
                .isEqualTo(SignalScoring.FALSE_START_PENALTY);
        assertThat(play.entry(result, "alice").fake()).isFalse();
        assertThat(play.entry(result, "bob").points()).isEqualTo(100);
    }

    @Test
    void whenEveryoneJumpsTheGunTheSignalEndsWithoutGo() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");

        play.jump("alice").jump("bob");

        assertThat(play.state()).isEqualTo("RESULT");
        SignalResult result = play.view("alice").results().getFirst();
        assertThat(result.fastestPlayerId()).isNull();
        assertThat(result.entries()).allSatisfy(e -> assertThat(e.falseStart()).isTrue());
    }

    @Test
    void tappingTheFakeOutIsAFalseStartAndARealGoFollows() {
        play.startWithFakeFirst(true, "alice", "bob");
        play.advanceToNextStep();
        assertThat(play.state()).isEqualTo("FAKE");
        assertThat(play.view("bob").fake()).isIn("HOLD", "STEADY", "FREEZE");

        play.jump("alice");
        assertThat(play.view("alice").me().fake()).isTrue();

        play.untilGo();
        assertThat(play.view("bob").fake()).as("the decoy is gone").isNull();
        play.react("bob", 280);

        SignalResult result = play.view("bob").results().getFirst();
        assertThat(result.fakeOut()).isTrue();
        assertThat(play.entry(result, "alice").fake()).isTrue();
        assertThat(play.entry(result, "alice").points())
                .isEqualTo(SignalScoring.FALSE_START_PENALTY);
        assertThat(play.entry(result, "bob").points()).isEqualTo(100);
    }

    @Test
    void disconnectedPlayersDoNotHoldUpASignal() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob", "carol");
        play.game().disconnect("carol");
        play.untilGo();

        play.react("alice", 210).react("bob", 260);

        assertThat(play.state()).isEqualTo("RESULT");
        Entry carol = play.entry(play.view("alice").results().getFirst(), "carol");
        assertThat(carol.points()).isZero();
        assertThat(carol.ms()).isNull();
    }

    @Test
    void aPlayerLeavingMidSignalEndsItWhenEveryoneElseHasTapped() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob", "carol");
        play.untilGo();
        play.react("alice", 210).react("bob", 260);
        assertThat(play.state()).isEqualTo("GO");

        play.game().leave("carol");

        assertThat(play.state()).isEqualTo("RESULT");
        assertThat(play.view("alice").results().getFirst().entries())
                .extracting(Entry::playerId)
                .containsExactly("alice", "bob");
    }

    @Test
    void theRoundEndsRightAfterTheLastSignalWithARoundTable() {
        play.start(Map.of("rounds", 1, "signals", 3, "fakeouts", false), "alice", "bob");
        long startedAt = play.game().now();

        for (int signal = 1; signal <= 3; signal++) {
            play.untilGo();
            assertThat(play.view("alice").signal()).isEqualTo(signal);
            play.react("alice", 150 + 20 * signal).react("bob", 300);
            assertThat(play.state()).isEqualTo("RESULT");
            assertThat(play.view("bob").results()).hasSize(signal);
        }
        play.advance(ReactionShowdownLogic.RESULT_MS);

        assertThat(play.game().phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(play.game().now() - startedAt)
                .as("ends with the signals, long before the deadline (BUG-05)")
                .isLessThan(ReactionShowdownLogic.maxTurnMillis(3, false) - 5000);
        assertThat(play.game().lastTurn().points())
                .containsEntry("alice", 300)
                .containsEntry("bob", 240);
        assertThat(play.game().lastTurn().headline()).isEqualTo("⚡ alice was quickest — 170 ms");

        ReactionShowdownView view = play.view("bob");
        assertThat(view.revealed()).isTrue();
        assertThat(view.table()).extracting(TableRow::playerId).containsExactly("alice", "bob");
        TableRow alice = view.table().getFirst();
        assertThat(alice.rank()).isEqualTo(1);
        assertThat(alice.bestMs()).isEqualTo(170);
        assertThat(alice.avgMs()).isEqualTo(190);
        assertThat(alice.points()).isEqualTo(300);
        assertThat(view.table().get(1).avgMs()).isEqualTo(300);
    }

    @Test
    void aRoundWithoutAnyTapStillEndsOnItsOwn() {
        play.start(Map.of("rounds", 1, "signals", 5, "fakeouts", true), "alice", "bob");

        play.game().finishTurn();

        assertThat(play.game().phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(play.game().lastTurn().points())
                .containsEntry("alice", 0)
                .containsEntry("bob", 0);
        assertThat(play.game().lastTurn().headline()).contains("Nobody tapped");
        assertThat(play.view("alice").results()).hasSize(5);
    }

    @Test
    void timersOfAFinishedRoundNeverReachTheNextOne() {
        play.start(Map.of("rounds", 3, "signals", 3, "fakeouts", true), "alice", "bob");
        play.game().finishTurn();
        int lastId = play.view("alice").signalId();

        play.game().nextTurn();
        assertThat(play.game().round()).isEqualTo(2);
        assertThat(play.view("alice").signalId()).isEqualTo(lastId + 1);
        assertThat(play.state()).isEqualTo("WAITING");

        play.advance(SignalPlan.MIN_WAIT_MS - 1);
        assertThat(play.state())
                .as("no stale timer skipped the wait (BUG-25)")
                .isEqualTo("WAITING");
        assertThat(play.view("alice").results()).isEmpty();
    }

    @Test
    void severalRoundsAddUpToThePodium() {
        play.start(Map.of("rounds", 3, "signals", 3, "fakeouts", false), "alice", "bob");

        for (int round = 1; round <= 3; round++) {
            assertThat(play.game().round()).isEqualTo(round);
            ReactionShowdownView fresh = play.view("bob");
            assertThat(fresh.results()).as("a new round starts clean (BUG-08)").isEmpty();
            assertThat(fresh.table()).isNull();
            assertThat(fresh.revealed()).isFalse();
            for (int signal = 1; signal <= 3; signal++) {
                play.untilGo();
                play.react("alice", 200).react("bob", 260);
            }
            play.game().finishTurn().nextTurn();
        }

        assertThat(play.game().phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(play.game().score("alice")).isEqualTo(900);
        assertThat(play.game().score("bob")).isEqualTo(720);
        assertThat(play.game().result().standings().getFirst().player().id()).isEqualTo("alice");
    }

    @Test
    void awardsGoToTheFastestTheSteadiestAndTheJumpiest() {
        play.start(Map.of("rounds", 1, "signals", 3, "fakeouts", false), "alice", "bob", "carol");

        play.jump("carol").untilGo();
        play.react("alice", 150).react("bob", 300);
        play.jumpLate("carol").untilGo();
        play.react("bob", 250).react("alice", 400);
        play.untilGo();
        play.react("bob", 260).react("alice", 420).react("carol", 500);
        play.game().finishGame();

        assertThat(play.game().result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Lightning", "alice", "150 ms"),
                        tuple("Steady hands", "bob", "⌀ 270 ms"),
                        tuple("Jumpy", "carol", "2 false starts"));
    }

    @Test
    void awardsAreSkippedWhenNobodyEarnedThem() {
        play.start(Map.of("rounds", 1, "signals", 3, "fakeouts", false), "alice", "bob");
        play.game().finishGame();

        assertThat(play.game().result().awards()).isEmpty();
    }

    /** Shared helpers: starts a game and plays signals deterministically on virtual time. */
    static final class Play {

        /** Network time the server sees on top of what the browser measured. */
        static final int NETWORK_MS = 20;

        private GameHarness game;
        private long goAt;

        void start(Map<String, ?> settings, String... players) {
            start(7, settings, players);
        }

        void start(long seed, Map<String, ?> settings, String... players) {
            game =
                    GameHarness.of(new ReactionShowdownGame())
                            .players(players)
                            .settings(settings)
                            .seed(seed)
                            .start();
        }

        /** Starts a game whose first signal has a planned fake-out ({@code true}) or none. */
        void startWithFakeFirst(boolean fakeFirst, String... players) {
            for (long seed = 1; seed < 500; seed++) {
                start(seed, Map.of("rounds", 1, "fakeouts", true), players);
                if (Boolean.valueOf(fakeFirst).equals(debug().get("fakeComing"))) {
                    return;
                }
            }
            throw new AssertionError("No seed found");
        }

        GameHarness game() {
            return game;
        }

        Map<String, Object> debug() {
            return game.debugState();
        }

        String state() {
            return (String) debug().get("state");
        }

        int signalId() {
            return ((Number) debug().get("signalId")).intValue();
        }

        ReactionShowdownView view(String playerId) {
            return game.view(playerId, ReactionShowdownView.class);
        }

        Entry entry(SignalResult result, String playerId) {
            return result.entries().stream()
                    .filter(e -> e.playerId().equals(playerId))
                    .findFirst()
                    .orElseThrow();
        }

        Play advance(long millis) {
            game.advance(Duration.ofMillis(millis));
            return this;
        }

        /** Fires the current signal's pending timer. */
        Play advanceToNextStep() {
            return advance(((Number) debug().get("nextInMs")).longValue());
        }

        /** Runs timers until the current (or next) signal shows GO. */
        Play untilGo() {
            while (!"GO".equals(state())) {
                assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
                advanceToNextStep();
            }
            goAt = game.now();
            return this;
        }

        /** Taps as if the browser measured {@code ms} since it rendered GO. */
        Play react(String player, int ms) {
            long due = goAt + ms + NETWORK_MS;
            if (game.now() < due) {
                advance(due - game.now());
            }
            game.act(player, "tap", Map.of("signal", signalId(), "ms", ms));
            return this;
        }

        /** Taps without having seen GO (a browser sends no time then). */
        Play jump(String player) {
            game.act(player, "tap", Map.of("signal", signalId()));
            return this;
        }

        /** Jumps the gun on the next signal: waits for its WAITING phase, then taps. */
        Play jumpLate(String player) {
            while (!"WAITING".equals(state())) {
                advanceToNextStep();
            }
            return jump(player);
        }
    }
}
