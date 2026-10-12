package com.example.funfridaygame.games.pictionary;

import static com.example.funfridaygame.games.pictionary.TestGames.ELEPHANT;
import static com.example.funfridaygame.games.pictionary.TestGames.drawer;
import static com.example.funfridaygame.games.pictionary.TestGames.drawing;
import static com.example.funfridaygame.games.pictionary.TestGames.feed;
import static com.example.funfridaygame.games.pictionary.TestGames.fixed;
import static com.example.funfridaygame.games.pictionary.TestGames.guess;
import static com.example.funfridaygame.games.pictionary.TestGames.ops;
import static com.example.funfridaygame.games.pictionary.TestGames.stroke;
import static com.example.funfridaygame.games.pictionary.TestGames.word;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import com.example.funfridaygame.testkit.GameHarness.Event;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scenario tests: plays Pictionary through the real engine with virtual time. */
class PictionaryGameTest {

    // ------------------------------------------------------------------ word choice & secrecy

    @Test
    void theDrawerPicksOneOfThreeWordsThatNobodyElseSees() {
        GameHarness game = TestGames.classic(Map.of(), "alice", "bob", "carol");

        Map<String, Object> alice = game.view("alice");
        assertThat(alice.get("phase")).isEqualTo("CHOOSING");
        assertThat(alice.get("drawerId")).isEqualTo("alice");
        assertThat((List<?>) alice.get("choices")).hasSize(3);
        assertThat(game.view("bob").get("choices")).isNull();
        assertThat(game.spectatorView().get("choices")).isNull();
        assertThat(game.view("bob").get("mask")).as("not even the length yet").isNull();
        assertThat(game.remainingMillis()).isEqualTo(10_000);

        game.act("alice", "choose", Map.of("index", 1));

        String word = word(game);
        assertThat((List<?>) game.debugState().get("choices")).element(1).isEqualTo(word);
        assertThat(game.view("alice").get("word")).isEqualTo(word);
        assertThat(game.view("alice").get("choices")).isNull();
        for (Map<String, Object> view :
                List.of(game.view("bob"), game.view("carol"), game.spectatorView())) {
            assertThat(view.get("word")).isNull();
            assertThat((String) view.get("mask"))
                    .hasSize(word.length())
                    .doesNotContainPattern("[a-z]");
        }
        assertThat(game.remainingMillis()).isEqualTo(80_000);
    }

    @Test
    void aWordIsPickedAutomaticallyWhenTheDrawerHesitates() {
        GameHarness game = TestGames.classic(Map.of(), "alice", "bob", "carol");
        List<?> choices = (List<?>) game.debugState().get("choices");

        game.advanceSeconds(9);
        assertThat(game.view("alice").get("phase")).isEqualTo("CHOOSING");
        game.advanceSeconds(1);

        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        assertThat(game.view("bob").get("phase")).isEqualTo("DRAWING");
        assertThat(word(game)).isIn(choices);
        assertThat(game.remainingMillis()).isBetween(79_000L, 80_000L);
    }

    @Test
    void nothingOfTheLastTurnLeaksIntoTheNextOne() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0))
                .act("alice", "stroke", stroke(1, 10, 10, 50, 50))
                .act("bob", "guess", guess(ELEPHANT))
                .act("carol", "guess", guess(ELEPHANT));
        assertThat(game.view("carol").get("word")).isEqualTo(ELEPHANT);

        game.nextTurn();

        Map<String, Object> carol = game.view("carol");
        assertThat(carol.get("drawerId")).isEqualTo("bob");
        assertThat(carol.get("revealed")).isEqualTo(false);
        assertThat(carol.get("word")).isNull();
        assertThat(carol.get("mask")).isNull();
        assertThat(feed(carol)).isEmpty();
        assertThat((List<?>) carol.get("correct")).isEmpty();
        assertThat(ops(carol)).isEmpty();
        assertThat(drawing(carol).get("turn")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ guessing & scoring

    @Test
    void correctGuessesScoreByTimeAndTheDrawerScoresPerGuesser() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));

        game.advanceSeconds(20).act("bob", "guess", guess("Elephant"));
        assertThat(game.phase()).as("carol still guesses").isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(20).act("carol", "guess", guess("elephant"));

        assertThat(game.phase()).as("everybody got it").isEqualTo(RunPhase.REVEAL);
        // bob: 40 + 60 * 60/80 = 85, +20 first; carol: 40 + 60 * 40/80 = 70; alice: 2 * 25
        assertThat(game.lastTurn().points())
                .containsExactlyInAnyOrderEntriesOf(Map.of("bob", 105, "carol", 70, "alice", 50));
        assertThat(game.lastTurn().headline()).isEqualTo("The word was “elephant”");

        Map<String, Object> reveal = game.view("carol");
        assertThat(reveal.get("word")).isEqualTo(ELEPHANT);
        assertThat(reveal.get("endReason")).isEqualTo("ALL_GUESSED");
        assertThat(reveal.get("drawerPoints")).isEqualTo(50);
        assertThat(correct(reveal))
                .extracting(c -> c.get("playerId"))
                .containsExactly("bob", "carol");
        assertThat(correct(reveal)).extracting(c -> c.get("points")).containsExactly(105, 70);
        assertThat(correct(reveal)).extracting(c -> c.get("ms")).containsExactly(20_000L, 40_000L);
    }

    @Test
    void theDrawerScoresAtMost150() {
        GameHarness game = fixed("a", "b", "c", "d", "e", "f", "g", "h");
        game.act("a", "choose", Map.of("index", 0));
        for (String guesser : List.of("b", "c", "d", "e", "f", "g", "h")) {
            game.act(guesser, "guess", guess(ELEPHANT));
        }
        assertThat(game.lastTurn().points()).containsEntry("a", 150).containsEntry("b", 120);
    }

    @Test
    void theTurnEndsWhenTimeRunsOut() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0)).act("bob", "guess", guess("mouse"));

        game.advanceSeconds(79.9);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.2);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).isEmpty();
        assertThat(game.lastTurn().headline()).isEqualTo("The word was “elephant”");
        assertThat(game.view("bob").get("word")).isEqualTo(ELEPHANT);
        assertThat(game.spectatorView().get("word")).isEqualTo(ELEPHANT);
        assertThat(game.view("bob").get("endReason")).isEqualTo("TIME_UP");
    }

    @Test
    void guessesShowUpLiveButTheFeedNeverRevealsTheWord() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));

        game.act("bob", "guess", guess("  big   grey  dog "));
        assertThat(feed(game.view("carol")))
                .singleElement()
                .satisfies(
                        line -> {
                            assertThat(line.get("kind")).isEqualTo("GUESS");
                            assertThat(line.get("text")).isEqualTo("big grey dog");
                            assertThat(line.get("playerId")).isEqualTo("bob");
                        });

        game.act("bob", "guess", guess("ELEPHANT"));
        Map<String, Object> carol = game.view("carol");
        assertThat(feed(carol).getLast().get("kind")).isEqualTo("CORRECT");
        assertThat(feed(carol).getLast().get("text")).isNull();
        assertThat(carol.get("word")).isNull();
        assertThat(carol.get("guessed")).isEqualTo(false);
        assertThat(carol.get("guessesLeft")).isEqualTo(30);
        assertThat(game.view("bob").get("guessed")).isEqualTo(true);
        assertThat(game.view("bob").get("word")).as("bob may see it now").isEqualTo(ELEPHANT);
        assertThat(game.view("alice").get("guessed")).as("the drawer").isNull();
        assertThat(feed(game.spectatorView())).hasSize(2);
        assertThat(correct(carol))
                .singleElement()
                .satisfies(c -> assertThat(c.get("points")).isNull());
    }

    @Test
    void nearMissesGetAPrivateSoCloseAndStayHidden() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));

        game.act("bob", "guess", guess("elephants"));
        game.act("bob", "guess", guess("a pink elephant"));
        game.act("bob", "guess", guess("elefant")); // two edits away: an ordinary wrong guess

        assertThat(game.privateEvents("bob"))
                .extracting(Event::type)
                .containsExactly("close", "close");
        assertThat(feed(game.view("bob")))
                .extracting(line -> line.get("kind"))
                .containsExactly("CLOSE", "CLOSE", "GUESS");
        assertThat(feed(game.view("carol")))
                .extracting(line -> line.get("text"))
                .containsExactly("elefant");
        assertThat(feed(game.spectatorView())).hasSize(1);
        assertThat(game.view("bob").get("guessed")).isEqualTo(false);
        assertThat(game.view("bob").get("guessesLeft")).isEqualTo(27);
    }

    @Test
    void guessesIgnoreCaseSpacesHyphensAndPunctuation() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 1)); // "ice cream"

        game.act("bob", "guess", guess("  ICE-Cream! "));
        game.act("carol", "guess", guess("icecream"));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsKeys("bob", "carol", "alice");
    }

    @Test
    void rejectsMovesThatBreakTheRules() {
        GameHarness game = fixed("alice", "bob", "carol");

        assertThat(game.reject("bob", "choose", Map.of("index", 0)).getMessage())
                .contains("alice picks");
        assertThat(game.reject("alice", "choose", Map.of("index", 3)).getMessage())
                .contains("index");
        assertThat(game.reject("bob", "guess", guess("cat")).getMessage())
                .contains("Wait until alice");
        assertThat(game.reject("alice", "stroke", stroke(1, 5, 5)).getMessage())
                .contains("Pick a word");
        assertThat(game.reject("alice", "dance", Map.of()).getMessage()).contains("Unknown");

        game.act("alice", "choose", Map.of("index", 0));
        assertThat(game.reject("alice", "choose", Map.of("index", 1)).getMessage())
                .contains("already");
        assertThat(game.reject("alice", "guess", guess(ELEPHANT)).getMessage()).contains("drawing");
        assertThat(game.reject("bob", "stroke", stroke(1, 5, 5)).getMessage())
                .contains("alice's turn");
        assertThat(game.reject("bob", "undo", Map.of()).getMessage()).contains("alice's turn");
        assertThat(game.reject("bob", "guess", guess("   ")).getMessage()).contains("text");
        assertThat(game.reject("bob", "guess", guess("?!")).getMessage()).contains("Type a word");

        game.act("bob", "guess", guess("cat"));
        assertThat(game.reject("bob", "guess", guess(" CAT ")).getMessage())
                .contains("already tried");
        game.act("bob", "guess", guess(ELEPHANT));
        assertThat(game.reject("bob", "guess", guess("dog")).getMessage())
                .contains("already guessed");
        assertThat(game.lastTurn()).isNull();
    }

    @Test
    void guessesAreCappedPerTurn() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));
        for (int i = 0; i < PictionaryLogic.MAX_GUESSES; i++) {
            game.act("bob", "guess", guess("guess " + i));
        }
        assertThat(game.reject("bob", "guess", guess(ELEPHANT)).getMessage())
                .contains("30 guesses");
        assertThat(game.view("bob").get("guessesLeft")).isEqualTo(0);
        assertThat(feed(game.view("carol"))).hasSize(30);
    }

    // ------------------------------------------------------------------ hints

    @Test
    void hintsRevealLettersAtHalfAndThreeQuartersOfTheTime() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));
        assertThat(game.view("bob").get("mask")).isEqualTo("________");

        game.advanceSeconds(39.9);
        assertThat(revealedLetters(game)).isZero();
        game.advanceSeconds(0.2);
        assertThat(revealedLetters(game)).isEqualTo(1);
        game.advanceSeconds(20);
        assertThat(revealedLetters(game)).isEqualTo(2);

        String mask = (String) game.view("carol").get("mask");
        for (int i = 0; i < mask.length(); i++) {
            assertThat(mask.charAt(i)).isIn('_', ELEPHANT.charAt(i));
        }
    }

    @Test
    void hintsCanBeSwitchedOff() {
        GameHarness game = fixed(Map.of("hints", false), "alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0)).advanceSeconds(75);
        assertThat(revealedLetters(game)).isZero();
    }

    // ------------------------------------------------------------------ leaving & disconnecting

    @Test
    void theDrawerLeavingEndsTheTurnWithoutPointsAndTheGameGoesOn() {
        GameHarness game = fixed("alice", "bob", "carol", "dave");
        game.act("alice", "choose", Map.of("index", 0)).act("bob", "guess", guess(ELEPHANT));

        game.leave("alice");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).isEmpty();
        assertThat(game.lastTurn().headline())
                .isEqualTo("alice left the game — no points this turn. The word was “elephant”");
        assertThat(game.view("bob").get("endReason")).isEqualTo("DRAWER_LEFT");
        assertThat(correct(game.view("bob")))
                .singleElement()
                .satisfies(c -> assertThat(c.get("points")).isEqualTo(0));

        game.nextTurn();
        assertThat(game.turn()).isEqualTo(2);
        assertThat(drawer(game)).isEqualTo("bob");
        game.finishGame();
        assertThat(game.result().standings())
                .extracting(s -> s.player().id())
                .doesNotContain("alice");
    }

    @Test
    void theDrawerLeavingWhileChoosingEndsTheTurnToo() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.leave("alice");
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline())
                .isEqualTo("alice left the game before picking a word — no points this turn");
        assertThat(game.view("bob").get("word")).isNull();
    }

    @Test
    void theDrawerLosingTheConnectionEndsTheTurnWithoutPoints() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0)).act("bob", "guess", guess(ELEPHANT));

        game.disconnect("alice");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).isEmpty();
        assertThat(game.lastTurn().headline()).startsWith("alice lost the connection — no points");
        assertThat(game.view("carol").get("endReason")).isEqualTo("DRAWER_OFFLINE");
    }

    @Test
    void drawersWhoAreOfflineOrGoneAreSkipped() {
        GameHarness game = fixed("alice", "bob", "carol", "dave");
        game.disconnect("bob").leave("carol");

        game.finishTurn().nextTurn();

        assertThat(game.turn()).as("bob (offline) and carol (gone) are skipped").isEqualTo(4);
        assertThat(drawer(game)).isEqualTo("dave");
    }

    @Test
    void aGuesserLeavingDoesNotBlockTheTurn() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0)).act("bob", "guess", guess(ELEPHANT));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);

        game.leave("carol");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points()).containsOnlyKeys("bob", "alice");
    }

    @Test
    void theTurnKeepsRunningWhileNoGuesserIsConnected() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));

        game.disconnect("bob").disconnect("carol");
        assertThat(game.phase()).as("they may come back").isEqualTo(RunPhase.PLAYING);

        game.reconnect("bob").act("bob", "guess", guess(ELEPHANT));
        assertThat(game.phase()).as("carol is still offline").isEqualTo(RunPhase.REVEAL);
    }

    @Test
    void theGameEndsWhenEverybodyElseLeft() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));

        game.leave("bob");
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.leave("carol");

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).startsWith("Everybody else left");
        game.finishGame();
        assertThat(game.result().standings())
                .extracting(s -> s.player().id())
                .containsExactly("alice");
    }

    // ------------------------------------------------------------------ full games & awards

    @Test
    void playsTwoRoundsWithEveryoneDrawingOnceAndNoWordTwice() {
        GameHarness game = TestGames.classic(Map.of("rounds", 2), "alice", "bob", "carol");
        List<String> order = List.of("alice", "bob", "carol");
        List<String> drawers = new ArrayList<>();
        Set<String> words = new HashSet<>();
        for (int turn = 0; turn < 6; turn++) {
            String drawer = drawer(game);
            drawers.add(drawer);
            game.act(drawer, "choose", Map.of("index", turn % 3));
            assertThat(words.add(word(game))).as("words never repeat").isTrue();
            String guesser = order.get((order.indexOf(drawer) + 1) % order.size());
            game.advanceSeconds(2 + turn).act(guesser, "guess", guess(word(game)));
            game.finishTurn();
            if (turn < 5) {
                game.nextTurn();
            }
        }
        assertThat(drawers).containsExactly("alice", "bob", "carol", "alice", "bob", "carol");
        assertThat(game.round()).isEqualTo(2);

        game.finishGame();

        assertThat(game.phase()).isEqualTo(RunPhase.FINISHED);
        assertThat(game.result().history()).hasSize(6);
        assertThat(game.result().awards())
                .extracting(Award::title)
                .containsExactly("Picasso", "Mind reader", "Quick eye");
    }

    @Test
    void awardsGoToTheBestDrawerTheFirstGuesserAndTheFastestEye() {
        GameHarness game = fixed("alice", "bob", "carol", "dave");
        // alice draws: bob (3 s) and carol guess
        game.act("alice", "choose", Map.of("index", 0)).advanceSeconds(3);
        game.act("bob", "guess", guess(ELEPHANT))
                .advanceSeconds(1)
                .act("carol", "guess", guess(ELEPHANT));
        game.advanceSeconds(1).act("dave", "guess", guess(ELEPHANT)).nextTurn();
        // bob draws: carol (9 s) first, then dave
        game.act("bob", "choose", Map.of("index", 1)).advanceSeconds(9);
        game.act("carol", "guess", guess(word(game))).act("dave", "guess", guess(word(game)));
        game.finishTurn().nextTurn();
        // carol draws: dave (2 s)
        game.act("carol", "choose", Map.of("index", 2)).advanceSeconds(2);
        game.act("dave", "guess", guess(word(game)));
        game.finishGame();

        assertThat(game.result().awards())
                .extracting(Award::title, Award::playerId, Award::detail)
                .containsExactly(
                        tuple("Picasso", "alice", "3 correct guesses on their drawings"),
                        tuple("Mind reader", "bob", "Guessed first 1 time"),
                        tuple("Quick eye", "dave", "Guessed a drawing in 2.0 s"));
    }

    @Test
    void theDrawingIsTheSameForEveryViewer() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0))
                .act("alice", "stroke", stroke(7, 100, 100, 200, 150))
                .act("bob", "guess", guess("dog"));

        assertThat(drawing(game.view("bob")))
                .isEqualTo(drawing(game.view("carol")))
                .isEqualTo(drawing(game.view("alice")))
                .isEqualTo(drawing(game.spectatorView()));
        assertThat(ops(game.spectatorView()))
                .singleElement()
                .satisfies(op -> assertThat(op.get("id")).isEqualTo(7));
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> correct(Map<String, Object> view) {
        return (List<Map<String, Object>>) view.get("correct");
    }

    private static long revealedLetters(GameHarness game) {
        String mask = (String) game.view("bob").get("mask");
        return mask.chars().filter(Character::isLetter).count();
    }
}
