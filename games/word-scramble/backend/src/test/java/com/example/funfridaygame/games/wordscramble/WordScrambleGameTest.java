package com.example.funfridaygame.games.wordscramble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays Word Scramble through the real engine with virtual time. */
class WordScrambleGameTest {

    private static GameHarness game(Map<String, ?> overrides, String... players) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("rounds", 3);
        settings.put("roundDuration", 45);
        settings.put("wordLength", "6");
        settings.putAll(overrides);
        return GameHarness.of(new WordScrambleGame())
                .players(players)
                .settings(settings)
                .seed(42)
                .start();
    }

    private static String word(GameHarness game) {
        return (String) game.debugState().get("word");
    }

    private static String scramble(GameHarness game) {
        return (String) game.debugState().get("scramble");
    }

    private static Map<String, Object> guess(String text) {
        return Map.of("text", text);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> me(GameHarness game, String playerId) {
        return (Map<String, Object>) game.view(playerId).get("me");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> status(GameHarness game, String viewer, String playerId) {
        return ((List<Map<String, Object>>) game.view(viewer).get("players"))
                .stream().filter(p -> playerId.equals(p.get("playerId"))).findFirst().orElseThrow();
    }

    /** Distinct wrong anagrams of {@code word} that also differ from {@code exclude}. */
    private static List<String> wrongAnagrams(String word, String exclude, int count) {
        List<String> found = new ArrayList<>();
        collect("", word, word, exclude, count, new HashSet<>(), found);
        return found;
    }

    private static void collect(
            String prefix,
            String rest,
            String word,
            String exclude,
            int count,
            Set<String> seen,
            List<String> found) {
        if (found.size() >= count) {
            return;
        }
        if (rest.isEmpty()) {
            if (!prefix.equals(word) && !prefix.equals(exclude) && seen.add(prefix)) {
                found.add(prefix);
            }
            return;
        }
        for (int i = 0; i < rest.length(); i++) {
            String remaining = rest.substring(0, i) + rest.substring(i + 1);
            collect(prefix + rest.charAt(i), remaining, word, exclude, count, seen, found);
        }
    }

    // ------------------------------------------------------------------ scoring and turn end

    @Test
    void solveOrderAndSpeedDecideThePointsAndTheRoundEndsWhenEveryoneSolved() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);

        game.advanceSeconds(9).act("alice", "guess", guess(word));
        assertThat(game.phase()).as("bob is still unscrambling").isEqualTo(RunPhase.PLAYING);
        assertThat(me(game, "alice")).containsEntry("solved", true).containsEntry("rank", 1);
        assertThat(me(game, "alice").get("timeMillis")).isEqualTo(9000L);
        assertThat(status(game, "bob", "alice")).containsEntry("solved", true);

        game.advanceSeconds(9).act("bob", "guess", guess(word));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        // 1st: 100 + 20 × 36/45 left = 116; 2nd: 80 + 20 × 27/45 left = 92.
        assertThat(game.lastTurn().points()).containsEntry("alice", 116).containsEntry("bob", 92);
        assertThat(game.lastTurn().headline())
                .isEqualTo("The word was " + word + " — alice was fastest (9.0 s)");
    }

    @Test
    void theDeadlineEndsTheRoundAndUnsolvedPlayersScoreNothing() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);
        game.act("bob", "guess", guess(wrongAnagrams(word, "", 1).getFirst()));

        game.advanceSeconds(44.9);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.2);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).isEmpty();
        assertThat(game.lastTurn().headline())
                .isEqualTo("The word was " + word + " — nobody cracked it");
        assertThat(status(game, "alice", "bob"))
                .containsEntry("solved", false)
                .containsEntry("misses", 1)
                .containsEntry("hintsUsed", 0);
    }

    @Test
    void wrongGuessesGiveFeedbackAndAllowRetries() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);
        List<String> misses = wrongAnagrams(word, scramble(game), 7);

        for (String miss : misses) {
            game.act("alice", "guess", guess(miss));
        }
        assertThat(me(game, "alice"))
                .containsEntry("solved", false)
                .containsEntry("misses", 7)
                .containsEntry("recentMisses", misses.reversed().subList(0, 5));

        game.act("alice", "guess", guess(word));
        assertThat(me(game, "alice")).containsEntry("solved", true).containsEntry("misses", 7);
    }

    @Test
    void guessesAreNormalisedOnArrival() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);
        String sloppy =
                "  " + word.substring(0, 2).toLowerCase(Locale.ROOT) + "-" + word.substring(2);

        game.act("alice", "guess", guess(sloppy + " \t"));

        assertThat(me(game, "alice")).containsEntry("solved", true);
    }

    @Test
    void hintsRevealLettersOnlyToThePlayerAndCostAQuarterOfTheRound() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);

        game.act("alice", "hint", null);

        assertThat(me(game, "alice").get("hints"))
                .isEqualTo(List.of(Map.of("position", 0, "letter", word.substring(0, 1))));
        assertThat(me(game, "bob").get("hints")).isEqualTo(List.of());
        assertThat(status(game, "bob", "alice").get("hintsUsed"))
                .as("hidden until the reveal")
                .isNull();

        game.advanceSeconds(9).act("alice", "guess", guess(word));
        game.advanceSeconds(9).act("bob", "guess", guess(word));

        // alice: (100 + 16) × 75 % = 87 — bob overtakes her with 80 + 12 = 92.
        assertThat(game.lastTurn().points()).containsEntry("alice", 87).containsEntry("bob", 92);
        assertThat(status(game, "bob", "alice")).containsEntry("hintsUsed", 1);
    }

    @Test
    void hintsAreLimitedAndCanBeSwitchedOff() {
        GameHarness game = game(Map.of(), "alice", "bob");
        game.act("alice", "hint", null).act("alice", "hint", null);
        assertThat((List<?>) me(game, "alice").get("hints")).hasSize(2);
        assertThat(game.reject("alice", "hint", null).getMessage()).contains("No hints left");

        GameHarness noHints = game(Map.of("hints", false), "alice", "bob");
        assertThat(noHints.view("alice")).containsEntry("maxHints", 0);
        assertThat(noHints.reject("alice", "hint", null).getMessage()).contains("switched off");
    }

    // ------------------------------------------------------------------ rejected actions

    @Test
    void rejectsInvalidGuessesWithoutCountingThem() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);
        String miss = wrongAnagrams(word, "", 1).getFirst();

        assertThat(game.reject("alice", "guess", guess(" !? ")).getMessage())
                .contains("Type a word");
        assertThat(game.reject("alice", "guess", guess("ab")).getMessage()).contains("6 letters");
        assertThat(game.reject("alice", "guess", guess("ZZZZZZ")).getMessage())
                .contains("letters on the tiles");
        assertThat(game.reject("alice", "guess", Map.of()).getMessage())
                .contains("text is missing");
        assertThat(game.reject("alice", "guess", guess("A".repeat(41))).getMessage())
                .contains("too long");
        assertThat(game.reject("alice", "dance", null).getMessage()).contains("Unknown action");

        game.act("alice", "guess", guess(miss));
        assertThat(game.reject("alice", "guess", guess(miss.toLowerCase(Locale.ROOT))).getMessage())
                .contains("already tried");
        assertThat(me(game, "alice")).containsEntry("misses", 1);

        game.act("alice", "guess", guess(word));
        assertThat(game.reject("alice", "guess", guess(word)).getMessage())
                .contains("already solved");
        assertThat(game.reject("alice", "hint", null).getMessage()).contains("already solved");
    }

    @Test
    void theMissCapStopsBruteForceAndCountsAsDone() {
        GameHarness game = game(Map.of("wordLength", "4"), "alice", "bob");
        String word = word(game);
        int cap = WordScrambleLogic.maxMisses(4);
        List<String> misses = wrongAnagrams(word, "", cap + 1);
        assertThat(game.view("alice")).containsEntry("maxMisses", cap);

        for (int i = 0; i < cap; i++) {
            game.act("alice", "guess", guess(misses.get(i)));
        }
        assertThat(game.reject("alice", "guess", guess(misses.get(cap))).getMessage())
                .contains("No guesses left");
        assertThat(game.reject("alice", "guess", guess(word)).getMessage())
                .contains("No guesses left");
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);

        game.act("bob", "guess", guess(word));
        assertThat(game.phase())
                .as("alice is out of guesses, bob solved")
                .isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsOnlyKeys("bob");
    }

    // ------------------------------------------------------------------ secrets

    @Test
    void theAnswerAndPrivateProgressNeverReachOtherViewsBeforeTheReveal() {
        GameHarness game = game(Map.of("rounds", 3), "alice", "bob");
        for (int round = 1; round <= 3; round++) {
            String word = word(game);
            String miss = wrongAnagrams(word, scramble(game), 1).getFirst();
            assertNoAnswer(game, word);

            game.act("alice", "guess", guess(miss)).act("alice", "hint", null);

            assertNoAnswer(game, word);
            assertThat(game.view("bob").toString()).doesNotContain(miss);
            assertThat(game.spectatorView().toString()).doesNotContain(miss);
            assertThat(game.spectatorView().get("me")).isNull();
            assertThat(me(game, "alice").get("recentMisses")).isEqualTo(List.of(miss));
            assertThat(me(game, "bob").get("hints")).isEqualTo(List.of());

            game.finishTurn();
            assertThat(game.view("bob"))
                    .containsEntry("revealed", true)
                    .containsEntry("word", word);
            assertThat(game.spectatorView()).containsEntry("word", word);
            game.nextTurn();
        }
        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
    }

    private static void assertNoAnswer(GameHarness game, String word) {
        for (Map<String, Object> view :
                List.of(game.view("alice"), game.view("bob"), game.spectatorView())) {
            assertThat(view).containsEntry("revealed", false);
            assertThat(view.get("word")).isNull();
            assertThat(view.toString()).doesNotContain(word);
        }
    }

    @Test
    void everyoneSeesTheSameProperScramble() {
        GameHarness game = game(Map.of(), "alice", "bob");
        String word = word(game);
        String scramble = scramble(game);

        assertThat(game.view("alice").get("scramble"))
                .isEqualTo(game.view("bob").get("scramble"))
                .isEqualTo(game.spectatorView().get("scramble"))
                .isEqualTo(scramble);
        assertThat(game.view("alice")).containsEntry("length", 6);
        assertThat(WordBank.signature(scramble)).isEqualTo(WordBank.signature(word));
        assertThat(Scrambler.isRotation(word, scramble)).isFalse();
    }

    // ------------------------------------------------------------------ roster changes

    @Test
    void disconnectedOrDepartedPlayersDoNotBlockTheRound() {
        GameHarness game = game(Map.of(), "alice", "bob", "carol");
        String word = word(game);

        game.act("alice", "guess", guess(word)).leave("alice");
        game.act("bob", "guess", guess(word));
        assertThat(game.phase()).as("carol is still unscrambling").isEqualTo(RunPhase.PLAYING);
        assertThat(status(game, "carol", "bob")).containsEntry("rank", 2);

        game.disconnect("carol");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsOnlyKeys("bob");

        game.nextTurn().reconnect("carol");
        game.act("carol", "guess", guess(word(game)));
        assertThat(me(game, "carol")).containsEntry("solved", true).containsEntry("rank", 1);
        assertThat((List<?>) game.view("bob").get("players")).hasSize(2);
    }

    // ------------------------------------------------------------------ full game, awards

    @Test
    void playsAFullGameToThePodiumWithAwards() {
        GameHarness game = game(Map.of("rounds", 3), "alice", "bob");

        String first = word(game);
        game.advanceSeconds(3).act("alice", "guess", guess(first));
        game.advanceSeconds(7).act("bob", "guess", guess(first));
        game.nextTurn();

        String second = word(game);
        for (String miss : wrongAnagrams(second, "", 3)) {
            game.act("bob", "guess", guess(miss));
        }
        game.advanceSeconds(5).act("bob", "guess", guess(second));
        game.advanceSeconds(1).act("alice", "guess", guess(second));
        game.nextTurn();

        String third = word(game);
        game.advanceSeconds(2).act("alice", "guess", guess(third));
        game.advanceSeconds(18).act("bob", "guess", guess(third));
        game.finishGame();

        assertThat(Set.of(first, second, third)).hasSize(3);
        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
        assertThat(game.score("alice")).isEqualTo(119 + 97 + 119);
        assertThat(game.score("bob")).isEqualTo(96 + 118 + 91);
        assertThat(game.result().awards())
                .containsExactly(
                        new Award("⚡", "Speed reader", "alice", third + " in 2.0 s"),
                        new Award("📚", "Wordsmith", "alice", "2 first solves"),
                        new Award("💪", "Never give up", "bob", second + " after 3 misses"));
    }

    @Test
    void wordsNeverRepeatWithinAGame() {
        GameHarness game = game(Map.of("rounds", 10, "wordLength", "4"), "alice", "bob");
        Set<String> words = new HashSet<>();
        for (int round = 1; round <= 10; round++) {
            words.add(word(game));
            game.finishTurn().nextTurn();
        }
        assertThat(words).hasSize(10);
        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
    }

    @Test
    void mixedLengthsDrawFiveToSevenLetters() {
        GameHarness game = game(Map.of("rounds", 10, "wordLength", "mixed"), "alice", "bob");
        Set<Integer> lengths = new HashSet<>();
        for (int round = 1; round <= 10; round++) {
            lengths.add(word(game).length());
            assertThat(game.view("alice").get("length")).isEqualTo(word(game).length());
            game.finishTurn().nextTurn();
        }
        assertThat(lengths).isSubsetOf(5, 6, 7).hasSizeGreaterThan(1);
    }

    // ------------------------------------------------------------------ settings

    @Test
    void defaultsSuitTheGame() {
        GameHarness game = GameHarness.of(new WordScrambleGame()).players("alice", "bob").start();

        assertThat(game.run().totalRounds()).isEqualTo(5);
        assertThat(game.remainingMillis()).as("45 s rounds (BUG-05)").isEqualTo(45_000);
        assertThat(word(game)).hasSize(6);
        assertThat(game.view("alice")).containsEntry("maxHints", 2).containsEntry("maxMisses", 40);
    }

    @Test
    void rejectsUnknownSettingValues() {
        assertThatThrownBy(() -> game(Map.of("wordLength", "9"), "alice", "bob"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining("Word length");
        assertThatThrownBy(() -> game(Map.of("roundDuration", 20), "alice", "bob"))
                .isInstanceOf(InvalidSettingsException.class);
    }
}
