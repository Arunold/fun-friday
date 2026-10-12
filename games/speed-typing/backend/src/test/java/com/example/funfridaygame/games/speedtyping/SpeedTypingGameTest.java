package com.example.funfridaygame.games.speedtyping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.games.speedtyping.SpeedTypingView.Lane;
import com.example.funfridaygame.games.speedtyping.SpeedTypingView.MyRace;
import com.example.funfridaygame.games.speedtyping.SpeedTypingView.ResultRow;
import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Scenario tests: plays Speed Typing through the real engine with virtual time. */
class SpeedTypingGameTest {

    // ------------------------------------------------------------------ helpers

    private static GameHarness game(Map<String, ?> settings, String... players) {
        Map<String, Object> values = new HashMap<>(Map.of("rounds", 1, "difficulty", "SHORT"));
        values.putAll(settings);
        return GameHarness.of(new SpeedTypingGame())
                .players(players)
                .settings(values)
                .seed(7)
                .start();
    }

    private static GameHarness game(String... players) {
        return game(Map.of(), players);
    }

    private static String text(GameHarness game) {
        return (String) game.debugState().get("text");
    }

    private static SpeedTypingView view(GameHarness game, String player) {
        return game.view(player, SpeedTypingView.class);
    }

    private static Lane lane(GameHarness game, String viewer, String player) {
        return view(game, viewer).lanes().stream()
                .filter(lane -> lane.playerId().equals(player))
                .findFirst()
                .orElseThrow();
    }

    private static ResultRow row(GameHarness game, String player) {
        return view(game, player).results().stream()
                .filter(row -> row.playerId().equals(player))
                .findFirst()
                .orElseThrow();
    }

    private static void send(
            GameHarness game, String player, String typed, int keystrokes, int errors) {
        game.act(
                player,
                "progress",
                Map.of("typed", typed, "keystrokes", keystrokes, "errors", errors));
    }

    /**
     * Types {@code text} at exactly {@code wpm}: the first character now, then an update every 150
     * ms, the last one {@code length / speed} after the first. {@code errors} wrong keystrokes
     * (typed and corrected again) are spread over the run.
     */
    private static void type(GameHarness game, String player, String text, int wpm, int errors) {
        double charsPerSecond = wpm * 5 / 60.0;
        long total = Math.round(text.length() / charsPerSecond * 1000);
        send(game, player, text.substring(0, 1), 1, 0);
        long elapsed = 0;
        while (elapsed < total) {
            long step = Math.min(150, total - elapsed);
            game.advance(Duration.ofMillis(step));
            elapsed += step;
            int chars =
                    elapsed >= total
                            ? text.length()
                            : Math.max(1, (int) (elapsed * charsPerSecond / 1000));
            int wrong = (int) ((long) errors * chars / text.length());
            send(game, player, text.substring(0, chars), chars + wrong, wrong);
        }
    }

    /**
     * Types {@code text[from, to)} one character per 200 ms (60 WPM) after {@code keystrokes}
     * earlier keystrokes; returns the keystroke count afterwards.
     */
    private static int typeChars(
            GameHarness game,
            String player,
            String text,
            int from,
            int to,
            int keystrokes,
            int errors) {
        int count = keystrokes;
        for (int chars = from + 1; chars <= to; chars++) {
            count++;
            send(game, player, text.substring(0, chars), count, errors);
            game.advance(Duration.ofMillis(200));
        }
        return count;
    }

    /** Types the first {@code chars} characters of {@code text} at 60 WPM. */
    private static void typePart(GameHarness game, String player, String text, int chars) {
        typeChars(game, player, text, 0, chars, 0, 0);
    }

    // ------------------------------------------------------------------ scoring

    @Test
    void finishersScoreByPlaceSpeedAndAccuracyAndTheRoundEndsEarly() {
        GameHarness game = game("alice", "bob");
        String text = text(game);

        type(game, "alice", text, 60, 0);
        assertThat(game.phase()).as("bob is still typing").isEqualTo(RunPhase.PLAYING);
        assertThat(view(game, "alice").me().rank()).isEqualTo(1);

        type(game, "bob", text, 48, 3);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);

        // alice: 1st (100) + 60 WPM / 4 (15), 100 % accurate.
        assertThat(game.lastTurn().points()).containsEntry("alice", 115);
        // bob: 2nd (80) + 48 WPM / 4 (12), times his accuracy.
        double bobAccuracy = (double) text.length() / (text.length() + 3);
        assertThat(game.lastTurn().points())
                .containsEntry("bob", (int) Math.round(92 * bobAccuracy));
        assertThat(game.lastTurn().headline())
                .isEqualTo("alice wins the race at 60 WPM, without a single typo!");

        ResultRow alice = row(game, "alice");
        assertThat(alice.rank()).isEqualTo(1);
        assertThat(alice.wpm()).isEqualTo(60);
        assertThat(alice.accuracy()).isEqualTo(100);
        assertThat(alice.timeMs()).isEqualTo(text.length() * 200L);
        ResultRow bob = row(game, "bob");
        assertThat(bob.rank()).isEqualTo(2);
        assertThat(bob.wpm()).isEqualTo(48);
        assertThat(bob.accuracy()).isEqualTo(text.length() * 100 / (text.length() + 3));
        assertThat(view(game, "bob").results())
                .extracting(ResultRow::playerId)
                .containsExactly("alice", "bob");
    }

    @Test
    void unfinishedPlayersScoreTheirCorrectPrefixWhenTimeRunsOut() {
        GameHarness game = game("alice", "bob", "carol");
        String text = text(game);
        int half = text.length() / 2;

        typePart(game, "alice", text, half);
        send(game, "bob", "qqqqqqqq", 8, 8);
        game.finishTurn();

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        int expected = (int) Math.round(40.0 * half / text.length());
        assertThat(game.lastTurn().points()).containsEntry("alice", expected);
        assertThat(game.lastTurn().points().getOrDefault("bob", 0)).isZero();
        assertThat(game.lastTurn().points()).doesNotContainKey("carol");
        assertThat(game.lastTurn().headline())
                .isEqualTo("Time's up! alice got furthest (" + half * 100 / text.length() + "%)");
        assertThat(row(game, "alice").finished()).isFalse();
        assertThat(row(game, "alice").timeMs()).isNull();
        assertThat(row(game, "carol").wpm()).as("carol never typed").isNull();
    }

    @Test
    void finishingBelowTheMinimumAccuracyHalvesThePoints() {
        GameHarness game = game(Map.of("minAccuracy", 95), "alice", "bob");
        String text = text(game);
        int errors = text.length() / 5; // about 83 % accuracy

        type(game, "alice", text, 60, errors);

        MyRace me = view(game, "alice").me();
        assertThat(me.finished()).isTrue();
        assertThat(me.penalized()).isTrue();
        assertThat(me.accuracy()).isLessThan(95);
        game.disconnect("bob");

        double accuracy = (double) text.length() / (text.length() + errors);
        assertThat(game.lastTurn().points())
                .containsEntry("alice", (int) Math.round(115 * accuracy * 0.5));
        assertThat(row(game, "alice").penalized()).isTrue();
    }

    // ------------------------------------------------------------------ typing rules

    @Test
    void progressCountsTheCorrectPrefixOnly() {
        GameHarness game = game("alice", "bob");
        String text = text(game);

        send(game, "alice", "zzzzzzzzzz", 10, 10);
        assertThat(lane(game, "bob", "alice").progress()).as("mashing keys").isZero();

        game.advanceSeconds(1);
        String typo = text.substring(0, 10) + "#" + text.substring(11, 16);
        send(game, "alice", typo, 26, 11);
        assertThat(lane(game, "bob", "alice").progress()).isEqualTo(10 * 100 / text.length());
    }

    @Test
    void aFullLengthTextWithTyposDoesNotFinishUntilItIsFixed() {
        GameHarness game = game("alice", "bob");
        String text = text(game);
        String typo = text.substring(0, 5) + "#" + text.substring(6);

        int keystrokes = typeChars(game, "alice", typo, 0, typo.length(), 0, 1);
        MyRace me = view(game, "alice").me();
        assertThat(me.finished()).isFalse();
        assertThat(me.typed()).hasSameSizeAs(text);
        assertThat(me.progress()).isEqualTo(5 * 100 / text.length());

        send(game, "alice", text.substring(0, 5), keystrokes, 1); // backspace to the typo
        typeChars(game, "alice", text, 5, text.length(), keystrokes, 1);
        assertThat(view(game, "alice").me().finished()).isTrue();
        assertThat(view(game, "alice").me().rank()).isEqualTo(1);
    }

    @Test
    void typingFasterThanHumanlyPossibleIsIgnored() {
        GameHarness game = game("alice", "bob");
        String text = text(game);
        int before = game.changes();

        send(game, "alice", text, text.length(), 0); // a paste
        assertThat(view(game, "alice").me().finished()).isFalse();
        assertThat(view(game, "alice").me().typed()).isEmpty();
        assertThat(game.changes()).as("nothing to broadcast").isEqualTo(before);

        send(game, "alice", text.substring(0, 15), 15, 0); // a burst after a network hiccup
        game.advance(Duration.ofMillis(100));
        send(game, "alice", text.substring(0, 30), 30, 0);
        assertThat(view(game, "alice").me().typed()).hasSize(15);

        game.advance(Duration.ofMillis(600)); // re-sent a moment later: fine at 25 chars/s
        send(game, "alice", text.substring(0, 30), 30, 0);
        assertThat(view(game, "alice").me().typed()).hasSize(30);
    }

    @Test
    void keystrokeAndErrorCountsAreClampedToWhatIsPossible() {
        GameHarness game = game("alice", "bob");
        String text = text(game);
        game.advance(Duration.ofMillis(400));

        send(game, "alice", text.substring(0, 3) + "##", 99_999, 0);

        MyRace me = view(game, "alice").me();
        assertThat(me.errors()).as("the two wrong characters").isEqualTo(2);
        assertThat(me.keystrokes()).isEqualTo(400 * 25 / 1000 + 15);
    }

    @Test
    void updatesAfterFinishingAreIgnored() {
        GameHarness game = game("alice", "bob", "carol");
        String text = text(game);
        type(game, "alice", text, 60, 0);
        int before = game.changes();

        send(game, "alice", text + "oops", text.length() + 4, 4);

        assertThat(game.changes()).isEqualTo(before);
        assertThat(view(game, "alice").me().typed()).isEqualTo(text);
        assertThat(view(game, "alice").me().rank()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidProgress() {
        GameHarness game = game("alice", "bob");
        String text = text(game);

        assertThat(game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown");
        assertThat(
                        game.reject("alice", "progress", Map.of("keystrokes", 1, "errors", 0))
                                .getMessage())
                .contains("typed");
        assertThat(
                        game.reject(
                                        "alice",
                                        "progress",
                                        Map.of(
                                                "typed",
                                                text + "x".repeat(11),
                                                "keystrokes",
                                                99,
                                                "errors",
                                                11))
                                .getMessage())
                .contains("longer than the text");
        assertThat(
                        game.reject(
                                        "alice",
                                        "progress",
                                        Map.of("typed", "T", "keystrokes", -1, "errors", 0))
                                .getMessage())
                .contains("keystrokes");
        assertThat(
                        game.reject(
                                        "alice",
                                        "progress",
                                        Map.of("typed", "T", "keystrokes", 1, "errors", 2))
                                .getMessage())
                .contains("more errors than keystrokes");
        assertThat(view(game, "alice").me().keystrokes()).isZero();
    }

    // ------------------------------------------------------------------ views

    @Test
    void typedTextIsOnlyEverSentToItsOwner() {
        GameHarness game = game("alice", "bob");
        String text = text(game);
        String typed = text.substring(0, 4) + "QXZ";

        typePart(game, "alice", typed, typed.length());

        MyRace me = view(game, "alice").me();
        assertThat(me.typed()).as("restores the input after a refresh").isEqualTo(typed);
        assertThat(me.keystrokes()).isEqualTo(typed.length());
        assertThat(me.errors()).isEqualTo(3);
        assertThat(me.startedAt()).isNotNull();
        assertThat(me.wpm()).as("live speed is computed in the browser").isNull();

        assertThat(game.view("bob").toString()).doesNotContain("QXZ");
        assertThat(game.spectatorView().toString()).doesNotContain("QXZ");
        assertThat(game.spectatorView().get("me")).isNull();
        Lane alice = lane(game, "bob", "alice");
        assertThat(alice.progress()).isEqualTo(4 * 100 / text.length());
        assertThat(alice.wpm()).as("speed shows once finished").isNull();
        assertThat(view(game, "bob").results()).isNull();
    }

    @Test
    void othersSeeSpeedAndPlaceOnceSomeoneFinished() {
        GameHarness game = game("alice", "bob");
        String text = text(game);

        type(game, "alice", text, 80, 0);

        Lane alice = lane(game, "bob", "alice");
        assertThat(alice.finished()).isTrue();
        assertThat(alice.progress()).isEqualTo(100);
        assertThat(alice.rank()).isEqualTo(1);
        assertThat(alice.wpm()).isEqualTo(80);
        assertThat(view(game, "bob").text()).isEqualTo(text);
        assertThat(view(game, "bob").difficulty()).isEqualTo("SHORT");
        assertThat(view(game, "bob").minAccuracy()).isEqualTo(90);
    }

    // ------------------------------------------------------------------ round flow

    @ParameterizedTest
    @CsvSource({"SHORT, 0, 45", "MEDIUM, 0, 75", "LONG, 0, 120", "SHORT, 90, 90", "LONG, 60, 60"})
    void theRoundTimerDependsOnTheTextLength(String difficulty, int roundTime, int seconds) {
        GameHarness game =
                game(Map.of("difficulty", difficulty, "roundTime", roundTime), "alice", "bob");

        assertThat(game.remainingMillis()).isEqualTo(seconds * 1000L);
        assertThat(view(game, "alice").endsAt()).isEqualTo(game.now() + seconds * 1000L);
        assertThat(text(game).length())
                .isBetween(
                        TextLength.valueOf(difficulty).minChars(),
                        TextLength.valueOf(difficulty).maxChars());
        game.advanceSeconds(seconds);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
    }

    @Test
    void longTextsNeedAtLeastAMinute() {
        assertThatThrownBy(() -> game(Map.of("difficulty", "LONG", "roundTime", 30), "a", "b"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("at least 60 s");
    }

    @Test
    void disconnectedOrDepartedPlayersDoNotBlockTheRound() {
        GameHarness game = game("alice", "bob", "carol");
        String text = text(game);

        typePart(game, "carol", text, 5);
        game.leave("carol");
        assertThat(view(game, "alice").lanes()).extracting(Lane::playerId).doesNotContain("carol");

        type(game, "alice", text, 60, 0);
        assertThat(game.phase()).as("bob is still connected").isEqualTo(RunPhase.PLAYING);

        game.disconnect("bob");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsKey("alice").doesNotContainKey("carol");
    }

    @Test
    void aReconnectingPlayerGetsTheirTypedTextBack() {
        GameHarness game = game("alice", "bob");
        String text = text(game);

        typePart(game, "bob", text, 12);
        game.disconnect("bob").reconnect("bob");

        assertThat(view(game, "bob").me().typed()).isEqualTo(text.substring(0, 12));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
    }

    @Test
    void aNewRoundStartsCleanWithAFreshText() {
        GameHarness game = game(Map.of("rounds", 3), "alice", "bob");
        String first = text(game);
        type(game, "alice", first, 60, 0);
        typePart(game, "bob", first, 10);
        game.finishTurn();
        assertThat(view(game, "bob").results()).isNotNull();

        game.nextTurn();

        assertThat(game.round()).isEqualTo(2);
        SpeedTypingView view = view(game, "alice");
        assertThat(view.text()).isNotEqualTo(first);
        assertThat(view.revealed()).isFalse();
        assertThat(view.results()).isNull();
        assertThat(view.me().typed()).isEmpty();
        assertThat(view.me().finished()).isFalse();
        assertThat(view.lanes()).allSatisfy(lane -> assertThat(lane.progress()).isZero());
        assertThat(game.view("alice").toString()).doesNotContain("rank=1");
    }

    @Test
    void textsDoNotRepeatWithinAGame() {
        GameHarness game = game(Map.of("rounds", 5), "alice", "bob");
        Set<String> texts = new LinkedHashSet<>();
        for (int round = 1; round <= 5; round++) {
            assertThat(game.round()).isEqualTo(round);
            texts.add(text(game));
            game.finishTurn().nextTurn();
        }
        assertThat(texts).hasSize(5);
        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
    }

    @Test
    void playsAFullGameWithAwards() {
        GameHarness game = game(Map.of("rounds", 3, "difficulty", "MEDIUM"), "alice", "bob");
        for (int round = 1; round <= 3; round++) {
            String text = text(game);
            type(game, "alice", text, 90, 0);
            type(game, "bob", text, 40, 12);
            assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
            game.nextTurn();
        }

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
        List<Award> awards = game.result().awards();
        assertThat(awards)
                .extracting(Award::title, Award::playerId)
                .containsExactly(
                        tuple("Fastest fingers", "alice"),
                        tuple("Sharpshooter", "alice"),
                        tuple("Marathon", "bob"));
        assertThat(awards.getFirst().detail()).isEqualTo("90 WPM");
        assertThat(awards.get(1).detail()).isEqualTo("100% average accuracy");
    }

    @Test
    void exposesTheTextForEndToEndTests() {
        GameHarness game = game(Map.of("difficulty", "MEDIUM"), "alice", "bob");

        assertThat(game.debugState()).containsEntry("difficulty", "MEDIUM").containsKey("text");
        assertThat(text(game)).isEqualTo(view(game, "alice").text());
    }
}
