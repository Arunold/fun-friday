package com.example.funfridaygame.games.triviablitz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.SettingDef;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays Trivia Blitz through the real engine with virtual time. */
class TriviaBlitzGameTest {

    private static GameHarness game(Map<String, ?> settings, String... players) {
        return GameHarness.of(new TriviaBlitzGame())
                .players(players)
                .settings(settings)
                .seed(42)
                .start();
    }

    private static GameHarness game(String... players) {
        return game(Map.of("rounds", 5, "roundDuration", 15), players);
    }

    private static int correct(GameHarness game) {
        return (Integer) game.debugState().get("correct");
    }

    private static int wrong(GameHarness game) {
        return (correct(game) + 1) % 4;
    }

    private static Map<String, Object> pick(int option) {
        return Map.of("option", option);
    }

    private static Map<?, ?> status(Map<String, Object> view, String playerId) {
        List<?> players = (List<?>) view.get("players");
        return players.stream()
                .map(s -> (Map<?, ?>) s)
                .filter(s -> playerId.equals(s.get("playerId")))
                .findFirst()
                .orElseThrow();
    }

    private static List<Object> pickers(Map<String, Object> view, int option) {
        return new ArrayList<>((List<?>) ((List<?>) view.get("pickers")).get(option));
    }

    // ------------------------------------------------------------------ scoring

    @Test
    void fasterCorrectAnswersScoreMore() {
        GameHarness game = game("alice", "bob");

        game.act("alice", "answer", pick(correct(game)));
        game.advanceSeconds(7.5).act("bob", "answer", pick(correct(game)));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsEntry("alice", 1000).containsEntry("bob", 750);
    }

    @Test
    void streaksAddABonusUpTo300AndWrongAnswersResetThem() {
        GameHarness game = game("alice", "bob");
        List<Integer> alice = new ArrayList<>();
        for (int round = 1; round <= 5; round++) {
            game.act("alice", "answer", pick(correct(game)));
            game.act("bob", "answer", pick(round == 2 ? correct(game) : wrong(game)));
            alice.add(game.lastTurn().points().get("alice"));
            if (round == 2) {
                assertThat(game.lastTurn().points()).containsEntry("bob", 1000);
            } else {
                assertThat(game.lastTurn().points()).doesNotContainKey("bob");
            }
            game.nextTurn();
        }

        // Instant answers: 1000 plus a streak bonus of 0, 100, 200, 300 and then capped at 300.
        assertThat(alice).containsExactly(1000, 1100, 1200, 1300, 1300);
        assertThat(game.score("alice")).isEqualTo(5900);
        assertThat(game.score("bob")).isEqualTo(1000);
    }

    @Test
    void theStreakIsShownAndALostStreakIsReported() {
        GameHarness game = game("alice", "bob");
        game.act("alice", "answer", pick(correct(game))).act("bob", "answer", pick(wrong(game)));
        assertThat(game.view("alice").get("myStreak")).isEqualTo(1);
        game.nextTurn();
        game.act("alice", "answer", pick(correct(game))).act("bob", "answer", pick(wrong(game)));
        assertThat(game.view("alice").get("myStreak")).isEqualTo(2);
        assertThat(status(game.view("bob"), "alice").get("streak")).isEqualTo(2);
        game.nextTurn();

        assertThat(game.view("alice").get("myStreak"))
                .as("unchanged until the reveal")
                .isEqualTo(2);
        game.act("alice", "answer", pick(wrong(game))).act("bob", "answer", pick(wrong(game)));

        Map<String, Object> view = game.view("bob");
        assertThat(status(view, "alice").get("streak")).isEqualTo(0);
        assertThat(status(view, "alice").get("lostStreak")).isEqualTo(2);
        assertThat(status(view, "bob").get("lostStreak")).as("only streaks of 2+").isNull();
        assertThat(game.view("alice").get("myStreak")).isEqualTo(0);
    }

    // ------------------------------------------------------------------ turn flow

    @Test
    void theTurnEndsAsSoonAsEveryoneHasAnswered() {
        GameHarness game = game("alice", "bob", "carol");

        game.act("alice", "answer", pick(0)).act("bob", "answer", pick(1));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        assertThat(game.view("carol").get("answeredCount")).isEqualTo(2);

        game.act("carol", "answer", pick(2));
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
    }

    @Test
    void theTurnEndsAtTheDeadlineWhenSomeoneDoesNotAnswer() {
        GameHarness game = game("alice", "bob");
        game.act("alice", "answer", pick(correct(game)));

        game.advanceSeconds(14.9);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.1);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsOnlyKeys("alice");
        assertThat(game.lastTurn().headline()).startsWith("The answer was ");
        assertThat(status(game.view("alice"), "bob").get("answered")).isEqualTo(false);
        assertThat(status(game.view("alice"), "bob").get("correct")).isEqualTo(false);
    }

    @Test
    void headlinesNameTheAnswer() {
        GameHarness game = game("alice", "bob");
        String answer = (String) game.debugState().get("answer");
        String letter = "ABCD".substring(correct(game), correct(game) + 1);
        game.finishTurn();
        assertThat(game.lastTurn().headline())
                .isEqualTo("Time's up — it was " + letter + ") " + answer);
        assertThat(game.lastTurn().points()).isEmpty();

        game.nextTurn();
        String second = (String) game.debugState().get("answer");
        game.act("alice", "answer", pick(wrong(game))).act("bob", "answer", pick(wrong(game)));
        assertThat(game.lastTurn().headline())
                .startsWith("Nobody got it — it was ")
                .endsWith(second);

        game.nextTurn();
        game.act("alice", "answer", pick(correct(game))).act("bob", "answer", pick(correct(game)));
        assertThat(game.lastTurn().headline()).startsWith("Everyone got it — ");
    }

    @Test
    void playsEveryRoundWithoutRepeatingAQuestionAndEndsOnThePodium() {
        GameHarness game = game(Map.of("rounds", 10), "alice", "bob");
        Set<String> questions = new HashSet<>();
        Set<String> categories = new HashSet<>();
        List<String> difficulties = new ArrayList<>();
        for (int round = 1; round <= 10; round++) {
            assertThat(game.round()).isEqualTo(round);
            Map<String, Object> view = game.view("alice");
            assertThat(view.get("number")).isEqualTo(round);
            questions.add((String) view.get("question"));
            categories.add((String) view.get("category"));
            difficulties.add((String) view.get("difficulty"));
            game.act("alice", "answer", pick(correct(game)))
                    .act("bob", "answer", pick(wrong(game)));
            game.nextTurn();
        }

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(questions).hasSize(10);
        assertThat(categories).as("a mixed game visits every category").hasSize(10);
        assertThat(difficulties)
                .as("a mixed difficulty ramps up: 4 easy, 3 medium, 3 hard")
                .containsExactly(
                        "easy", "easy", "easy", "easy", "medium", "medium", "medium", "hard",
                        "hard", "hard");
        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
    }

    @Test
    void aCategoryAndDifficultyFilterIsRespected() {
        GameHarness game =
                game(
                        Map.of("rounds", 5, "category", "science", "difficulty", "hard"),
                        "alice",
                        "bob");
        for (int round = 1; round <= 5; round++) {
            Map<String, Object> view = game.view("bob");
            assertThat(view.get("category")).isEqualTo("Science");
            assertThat(view.get("categoryIcon")).isEqualTo("🔬");
            assertThat(view.get("difficulty")).isEqualTo("hard");
            game.finishTurn().nextTurn();
        }
        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
    }

    // ------------------------------------------------------------------ actions

    @Test
    void rejectsInvalidAndRepeatedAnswers() {
        GameHarness game = game("alice", "bob");

        assertThat(game.reject("alice", "jump", Map.of()).getMessage()).contains("Unknown action");
        assertThat(game.reject("alice", "answer", Map.of()).getMessage())
                .isEqualTo("option must be A, B, C or D (0–3)");
        assertThat(game.reject("alice", "answer", pick(4)).getMessage()).contains("A, B, C or D");
        assertThat(game.reject("alice", "answer", pick(-1)).getMessage()).contains("A, B, C or D");
        assertThat(game.reject("alice", "answer", Map.of("option", "first")).getMessage())
                .contains("Invalid");
        assertThat(game.reject("mallory", "answer", pick(0)).getMessage()).contains("not playing");

        game.act("alice", "answer", pick(1));
        assertThat(game.reject("alice", "answer", pick(2)).getMessage())
                .contains("already locked in");
        assertThat(game.view("alice").get("myAnswer")).as("the first pick stays").isEqualTo(1);

        game.act("bob", "answer", pick(3));
        assertThat(game.reject("bob", "answer", pick(0)).getMessage()).contains("next turn");
    }

    // ------------------------------------------------------------------ secrets

    @Test
    void theAnswerAndOtherPicksStayHiddenUntilTheReveal() {
        GameHarness game = game("alice", "bob");
        int correct = correct(game);
        game.advanceSeconds(2).act("alice", "answer", pick(correct));

        Map<String, Object> alice = game.view("alice");
        assertThat(alice.get("revealed")).isEqualTo(false);
        assertThat(alice.get("myAnswer")).isEqualTo(correct);
        assertThat(alice.get("correct")).isNull();
        assertThat(alice.get("pickers")).isNull();
        assertThat(alice.get("fastest")).isNull();
        assertThat(alice.get("fact")).isNull();

        Map<String, Object> bob = game.view("bob");
        assertThat(bob.get("myAnswer")).isNull();
        assertThat(bob.get("myStreak")).isEqualTo(0);
        assertThat(bob.get("answeredCount")).isEqualTo(1);
        assertThat(status(bob, "alice").get("answered")).isEqualTo(true);
        assertThat(status(bob, "alice").get("correct")).isNull();
        assertThat(status(bob, "alice").get("answerMillis")).isNull();

        Map<String, Object> spectator = game.spectatorView();
        assertThat(spectator.get("myAnswer")).isNull();
        assertThat(spectator.get("myStreak")).isNull();
        assertThat(spectator.get("correct")).isNull();
        assertThat(spectator.get("pickers")).isNull();
        assertThat(spectator.get("options")).isEqualTo(alice.get("options"));

        int bobsPick = (correct + 2) % 4;
        game.act("bob", "answer", pick(bobsPick));

        Map<String, Object> revealed = game.spectatorView();
        assertThat(revealed.get("revealed")).isEqualTo(true);
        assertThat(revealed.get("correct")).isEqualTo(correct);
        assertThat(pickers(revealed, correct)).containsExactly("alice");
        assertThat(pickers(revealed, bobsPick)).containsExactly("bob");
        Map<?, ?> fastest = (Map<?, ?>) revealed.get("fastest");
        assertThat(fastest.get("playerId")).isEqualTo("alice");
        assertThat(fastest.get("millis")).isEqualTo(2000L);
        assertThat(status(revealed, "alice").get("answerMillis")).isEqualTo(2000L);
        assertThat(status(revealed, "bob").get("correct")).isEqualTo(false);
    }

    @Test
    void theNextQuestionStartsWithoutTheAnswer() {
        GameHarness game = game("alice", "bob");
        game.act("alice", "answer", pick(0)).act("bob", "answer", pick(1));
        assertThat(game.view("alice").get("correct")).isNotNull();

        game.nextTurn();

        // BUG-08: the first view of a new round must not carry anything from the last reveal.
        for (Map<String, Object> view :
                List.of(game.view("alice"), game.view("bob"), game.spectatorView())) {
            assertThat(view.get("revealed")).isEqualTo(false);
            assertThat(view.get("correct")).isNull();
            assertThat(view.get("pickers")).isNull();
            assertThat(view.get("fastest")).isNull();
            assertThat(view.get("fact")).isNull();
            assertThat(view.get("answeredCount")).isEqualTo(0);
        }
        assertThat(game.view("alice").get("myAnswer")).isNull();
    }

    @Test
    void everyoneSeesTheSameShuffledOptions() {
        GameHarness game = game(Map.of("rounds", 20), "alice", "bob");
        Set<Integer> correctPositions = new HashSet<>();
        for (int round = 1; round <= 20; round++) {
            Map<String, Object> state = game.debugState();
            List<?> options = (List<?>) game.view("alice").get("options");
            assertThat(game.view("bob").get("options")).isEqualTo(options);
            assertThat(game.spectatorView().get("options")).isEqualTo(options);
            assertThat(options).hasSize(4).doesNotHaveDuplicates();
            assertThat(options.get((Integer) state.get("correct"))).isEqualTo(state.get("answer"));
            assertThat(game.view("alice").get("question")).isEqualTo(state.get("question"));
            correctPositions.add((Integer) state.get("correct"));
            game.finishTurn().nextTurn();
        }
        assertThat(correctPositions).as("the correct answer moves around").hasSizeGreaterThan(2);
    }

    // ------------------------------------------------------------------ players coming and going

    @Test
    void disconnectedOrDepartedPlayersDoNotBlockTheTurn() {
        GameHarness game = game("alice", "bob", "carol");
        int correct = correct(game);

        game.act("alice", "answer", pick(correct)).leave("alice");
        game.act("bob", "answer", pick(correct));
        assertThat(game.phase()).as("carol still has to answer").isEqualTo(RunPhase.PLAYING);

        game.disconnect("carol");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsOnlyKeys("bob");

        Map<String, Object> view = game.view("bob");
        assertThat(pickers(view, correct)).containsExactly("bob");
        assertThat((List<?>) view.get("players")).hasSize(2);
        assertThat(((Map<?, ?>) view.get("fastest")).get("playerId")).isEqualTo("bob");

        game.nextTurn().reconnect("carol");
        game.act("bob", "answer", pick(0));
        assertThat(game.phase()).as("carol is back and must answer").isEqualTo(RunPhase.PLAYING);
        game.act("carol", "answer", pick(1));
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
    }

    // ------------------------------------------------------------------ awards

    @Test
    void awardsGoToTheSharpestTheFastestAndTheLongestStreak() {
        GameHarness game = game("alice", "bob", "carol");
        for (int round = 1; round <= 5; round++) {
            int correct = correct(game);
            game.advanceSeconds(1).act("bob", "answer", pick(round <= 2 ? correct : wrong(game)));
            game.advanceSeconds(2).act("alice", "answer", pick(correct));
            game.finishTurn().nextTurn(); // carol never answers: every turn runs to the deadline
        }

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(game.result().standings().getFirst().player().id()).isEqualTo("alice");
        assertThat(game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Brainiac", "alice", "5 of 5 correct"),
                        tuple("Quick draw", "bob", "1.0 s on average"),
                        tuple("On fire", "alice", "5 in a row"));
    }

    @Test
    void noAwardsWhenNobodyAnswersCorrectly() {
        GameHarness game = game("alice", "bob");
        game.finishGame();
        assertThat(game.result().awards()).isEmpty();
    }

    // ------------------------------------------------------------------ settings

    @Test
    void rejectsFiltersWithFewerQuestionsThanRounds() {
        Map<String, Object> settings =
                Map.of("rounds", 20, "category", "history", "difficulty", "easy");
        assertThatThrownBy(() -> game(settings, "alice", "bob"))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessage(
                        "Only 7 questions match History · Easy — play 5 rounds or pick a broader"
                                + " category or difficulty.");
    }

    @Test
    void everySingleCategorySupportsTheLongestGame() {
        for (Category category : Category.values()) {
            GameHarness game =
                    game(Map.of("rounds", 20, "category", category.id()), "alice", "bob");
            assertThat(game.view("alice").get("category")).isEqualTo(category.label());
        }
    }

    @Test
    void offersTheAgreedSettings() {
        TriviaBlitzGame definition = new TriviaBlitzGame();
        assertThat(definition.info().id()).isEqualTo("trivia-blitz");
        assertThat(definition.settings())
                .extracting(SettingDef::key, SettingDef::defaultValue)
                .containsExactly(
                        tuple("rounds", 10),
                        tuple("roundDuration", 15),
                        tuple("category", "mixed"),
                        tuple("difficulty", "mixed"));
        assertThat(definition.settings().get(0).options())
                .extracting(SettingDef.Option::value)
                .containsExactly(5, 10, 15, 20);
        assertThat(definition.settings().get(1).options())
                .extracting(SettingDef.Option::value)
                .containsExactly(10, 15, 20, 30);
        assertThat(definition.settings().get(2).options()).hasSize(Category.values().length + 1);
        assertThat(definition.settings().get(3).options())
                .extracting(SettingDef.Option::value)
                .containsExactly("mixed", "easy", "medium", "hard");
    }
}
