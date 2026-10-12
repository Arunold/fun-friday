package com.example.funfridaygame.games.reactionshowdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownGameTest.Play;
import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.SettingsResolver;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for the rules around a tap: validation, fair timing, hidden information and
 * settings.
 */
class ReactionShowdownRulesTest {

    private final Play play = new Play();

    @Test
    void rejectsInvalidTaps() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob", "carol");
        GameHarness game = play.game();
        int id = play.signalId();

        assertThat(game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown action");
        assertThat(game.reject("alice", "tap", Map.of()).getMessage()).contains("signal");
        assertThat(game.reject("alice", "tap", Map.of("signal", id, "ms", -5)).getMessage())
                .contains("ms");
        assertThat(game.reject("alice", "tap", Map.of("signal", id + 1, "ms", 200)).getMessage())
                .contains("That signal is over");

        play.jump("carol");
        assertThat(game.reject("carol", "tap", Map.of("signal", id)).getMessage())
                .contains("False start");

        play.untilGo().react("alice", 240);
        assertThat(game.reject("alice", "tap", Map.of("signal", id, "ms", 250)).getMessage())
                .contains("already tapped");

        play.advanceToNextStep();
        assertThat(play.state()).isEqualTo("RESULT");
        assertThat(game.reject("bob", "tap", Map.of("signal", id, "ms", 1900)).getMessage())
                .contains("Too late");
    }

    @Test
    void reactionTimesAreCheckedAgainstTheServerClock() {
        assertThat(ReactionShowdownLogic.timed(400, 230)).isEqualTo(Reaction.valid(230));
        assertThat(ReactionShowdownLogic.timed(400, 540))
                .as("within the tolerance")
                .isEqualTo(Reaction.valid(540));
        assertThat(ReactionShowdownLogic.timed(400, 900))
                .as("implausibly slow claim: the server's time counts")
                .isEqualTo(Reaction.valid(400));
        assertThat(ReactionShowdownLogic.timed(400, 99))
                .as("anticipation")
                .isEqualTo(Reaction.falseStart(Reaction.Foul.EARLY));
        assertThat(ReactionShowdownLogic.timed(60, 400))
                .as("capped to an impossible time")
                .isEqualTo(Reaction.falseStart(Reaction.Foul.EARLY));
        assertThat(ReactionShowdownLogic.timed(400, null))
                .as("tapped before GO reached the screen")
                .isEqualTo(Reaction.falseStart(Reaction.Foul.EARLY));
    }

    @Test
    void networkLatencyDoesNotDecideWhoIsFastest() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        play.untilGo();

        // Bob's tap reaches the server first, but Alice reacted faster on her screen (IMP-21).
        play.advance(260);
        play.game().act("bob", "tap", Map.of("signal", play.signalId(), "ms", 240));
        play.advance(150);
        play.game().act("alice", "tap", Map.of("signal", play.signalId(), "ms", 190));

        assertThat(play.view("alice").results().getFirst().fastestPlayerId()).isEqualTo("alice");
    }

    @Test
    void tapsDuringGoWithoutATimeOrFasterThanHumanlyPossibleAreFalseStarts() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        play.untilGo();

        play.jump("alice");
        play.react("bob", 60);

        assertThat(play.state()).isEqualTo("RESULT");
        assertThat(play.view("alice").results().getFirst().entries())
                .allSatisfy(
                        e -> assertThat(e.points()).isEqualTo(SignalScoring.FALSE_START_PENALTY));
    }

    @Test
    void upcomingSignalsNeverAppearInViews() {
        play.startWithFakeFirst(true, "alice", "bob");
        assertThat(play.debug())
                .containsEntry("fakeComing", true)
                .containsEntry("state", "WAITING");
        GameHarness game = play.game();

        for (Map<String, Object> view :
                List.of(game.view("alice"), game.view("bob"), game.spectatorView())) {
            assertThat(view.keySet())
                    .as("nothing about upcoming signals is sent (BUG-08, BUG-31)")
                    .containsExactlyInAnyOrder(
                            "signal",
                            "signals",
                            "signalId",
                            "state",
                            "fake",
                            "fakeouts",
                            "revealed",
                            "me",
                            "players",
                            "results",
                            "table");
            assertThat(view.get("state")).isEqualTo("WAITING");
            assertThat(view.get("fake")).isNull();
        }

        play.advanceToNextStep();
        assertThat(game.view("bob").get("fake"))
                .as("shown only while on screen")
                .isIn("HOLD", "STEADY", "FREEZE");
    }

    @Test
    void theDeadlineDoesNotDependOnTheDrawnSignals() {
        long expected = ReactionShowdownLogic.maxTurnMillis(3, true);
        for (long seed = 1; seed <= 5; seed++) {
            play.start(seed, Map.of("rounds", 1), "alice", "bob");
            assertThat(play.game().remainingMillis()).isEqualTo(expected);
        }
        assertThat(expected).isEqualTo(29_000);
        assertThat(ReactionShowdownLogic.maxTurnMillis(5, false)).isEqualTo(39_500);
    }

    @Test
    void otherPlayersTimesStayHiddenUntilTheSignalIsOver() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        play.untilGo();
        play.react("alice", 234);

        ReactionShowdownView mine = play.view("alice");
        assertThat(mine.me().tapped()).isTrue();
        assertThat(mine.me().ms()).isEqualTo(234);

        Map<String, Object> bobs = play.game().view("bob");
        assertThat(bobs.toString()).doesNotContain("234");
        assertThat(play.view("bob").players())
                .filteredOn(p -> p.playerId().equals("alice"))
                .singleElement()
                .satisfies(p -> assertThat(p.tapped()).isTrue());

        play.react("bob", 300);
        assertThat(play.game().view("bob").toString()).contains("234");
    }

    @Test
    void spectatorsSeeTheFieldButNoPersonalStatus() {
        play.start(Map.of("rounds", 1, "fakeouts", false), "alice", "bob");
        play.untilGo();
        play.react("alice", 250);

        Map<String, Object> spectator = play.game().spectatorView();
        assertThat(spectator.get("state")).isEqualTo("GO");
        assertThat(spectator.get("me")).isNull();
        assertThat((List<?>) spectator.get("players")).hasSize(2);
        assertThat(play.view("alice").me()).isNotNull();
    }

    @Test
    void settingsOfferRoundsSignalsAndFakeOutsButNoRoundTimer() {
        ReactionShowdownGame definition = new ReactionShowdownGame();

        assertThat(definition.info().id()).isEqualTo(ReactionShowdownGame.ID);
        assertThat(definition.settings())
                .extracting(SettingDef::key)
                .containsExactly(GameSettings.ROUNDS, "signals", "fakeouts")
                .doesNotContain(GameSettings.ROUND_DURATION);
        assertThat(SettingsResolver.defaults(definition))
                .containsEntry("rounds", 3)
                .containsEntry("signals", 3)
                .containsEntry("fakeouts", true);
        assertThatThrownBy(
                        () ->
                                GameHarness.of(definition)
                                        .players("alice", "bob")
                                        .settings(Map.of("signals", 4))
                                        .start())
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("Signals per round");
    }

    @Test
    void fiveSignalsMakeARound() {
        play.start(Map.of("rounds", 1, "signals", 5, "fakeouts", false), "alice", "bob");
        for (int signal = 1; signal <= 5; signal++) {
            play.untilGo();
            assertThat(play.view("bob").signal()).isEqualTo(signal);
            assertThat(play.view("bob").signals()).isEqualTo(5);
            play.react("bob", 210).react("alice", 330);
        }
        play.game().finishTurn();

        assertThat(play.game().lastTurn().points())
                .containsEntry("bob", 500)
                .containsEntry("alice", 400);
    }
}
