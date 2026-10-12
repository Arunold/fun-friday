package com.example.funfridaygame.games.pictionary;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.games.pictionary.WordMatcher.Verdict;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Word lists, word choice, guess matching, hints, scoring and settings. */
class WordsAndRulesTest {

    @Test
    void theWordListsAreCuratedAndLargeEnough() {
        WordList list = WordList.standard();
        Set<String> all = new HashSet<>();
        for (WordList.Level level : WordList.Level.values()) {
            List<String> words = list.words(level);
            assertThat(words).as(level + " words").hasSizeGreaterThanOrEqualTo(80);
            assertThat(words).allMatch(w -> w.matches("[a-z]+([ -][a-z]+)*"));
            for (String word : words) {
                assertThat(all.add(word)).as("'%s' appears only once", word).isTrue();
            }
        }
    }

    @Test
    void mixedModeOffersOneWordOfEachLevelAndDrawnWordsNeverReturn() {
        WordPicker picker = new WordPicker(WordList.standard(), WordPicker.Mode.MIXED);
        Random random = new Random(3);
        Set<String> drawn = new HashSet<>();
        for (int turn = 0; turn < 30; turn++) {
            List<WordPicker.Choice> choices = picker.next(random);
            assertThat(choices)
                    .extracting(WordPicker.Choice::level)
                    .containsExactly(
                            WordList.Level.EASY, WordList.Level.MEDIUM, WordList.Level.HARD);
            String word = choices.get(turn % 3).word();
            assertThat(drawn.add(word)).isTrue();
            picker.markChosen(word);
        }
    }

    @Test
    void offeredWordsOnlyComeBackWhenALevelRunsOut() {
        WordPicker picker = new WordPicker(WordList.standard(), WordPicker.Mode.HARD);
        Random random = new Random(5);
        int size = WordList.standard().words(WordList.Level.HARD).size();
        List<String> offered = new ArrayList<>();
        Set<String> drawn = new HashSet<>();
        for (int turn = 0; turn < size / 3; turn++) {
            List<WordPicker.Choice> choices = picker.next(random);
            assertThat(choices.stream().map(WordPicker.Choice::word).collect(Collectors.toSet()))
                    .hasSize(3);
            choices.forEach(c -> offered.add(c.word()));
            picker.markChosen(choices.getFirst().word());
            drawn.add(choices.getFirst().word());
        }
        assertThat(new HashSet<>(offered))
                .as("no repeats while fresh words remain")
                .hasSize(offered.size());

        for (int turn = 0; turn < 20; turn++) {
            picker.next(random).forEach(c -> assertThat(drawn).doesNotContain(c.word()));
        }
    }

    @Test
    void guessesAreJudgedLeniently() {
        assertThat(WordMatcher.judge("Elephant", "elephant")).isEqualTo(Verdict.CORRECT);
        assertThat(WordMatcher.judge("  ice-cream!! ", "ice cream")).isEqualTo(Verdict.CORRECT);
        assertThat(WordMatcher.judge("hotdog", "hot dog")).isEqualTo(Verdict.CORRECT);
        assertThat(WordMatcher.judge("Café", "cafe")).isEqualTo(Verdict.CORRECT);
        assertThat(WordMatcher.judge("xray", "x-ray")).isEqualTo(Verdict.CORRECT);

        assertThat(WordMatcher.judge("elephnt", "elephant")).isEqualTo(Verdict.CLOSE);
        assertThat(WordMatcher.judge("elehpant", "elephant"))
                .as("swapped letters")
                .isEqualTo(Verdict.CLOSE);
        assertThat(WordMatcher.judge("elephants", "elephant")).isEqualTo(Verdict.CLOSE);
        assertThat(WordMatcher.judge("an elephant?", "elephant")).isEqualTo(Verdict.CLOSE);
        assertThat(WordMatcher.judge("black cat", "cat")).isEqualTo(Verdict.CLOSE);

        assertThat(WordMatcher.judge("cats", "cat"))
                .as("short words need it exact")
                .isEqualTo(Verdict.WRONG);
        assertThat(WordMatcher.judge("elefant", "elephant")).isEqualTo(Verdict.WRONG);
        assertThat(WordMatcher.judge("concatenate", "cat")).isEqualTo(Verdict.WRONG);
        assertThat(WordMatcher.judge("ice", "ice cream")).isEqualTo(Verdict.WRONG);
    }

    @Test
    void editDistanceCountsOneChangeAndStopsAtTwo() {
        assertThat(WordMatcher.distance("guitar", "guitar")).isZero();
        assertThat(WordMatcher.distance("guitar", "gitar")).isEqualTo(1);
        assertThat(WordMatcher.distance("guitar", "guiter")).isEqualTo(1);
        assertThat(WordMatcher.distance("guitar", "giutar")).isEqualTo(1);
        assertThat(WordMatcher.distance("guitar", "guitars")).isEqualTo(1);
        assertThat(WordMatcher.distance("guitar", "gutiars")).isEqualTo(2);
        assertThat(WordMatcher.distance("guitar", "g")).isEqualTo(2);
    }

    @Test
    void theMaskKeepsSpacesAndHyphensAndRevealsAThirdOfTheLettersAtMost() {
        HintMask mask = new HintMask("jack-in-the-box", new Random(1));
        assertThat(mask.masked()).isEqualTo("____-__-___-___");
        assertThat(mask.letters()).isEqualTo(12);
        assertThat(mask.firstHintLetters() + mask.secondHintLetters()).isEqualTo(4);

        mask.reveal(mask.firstHintLetters());
        mask.reveal(mask.secondHintLetters());
        mask.reveal(10);
        assertThat(mask.masked().chars().filter(Character::isLetter).count()).isEqualTo(12);

        HintMask cat = new HintMask("cat", new Random(1));
        assertThat(cat.firstHintLetters()).isZero();
        assertThat(cat.secondHintLetters()).isEqualTo(1);
        assertThat(new HintMask("ice cream", new Random(1)).masked()).isEqualTo("___ _____");
    }

    @Test
    void scoringFollowsTheRules() {
        assertThat(Scoring.guesser(0, 80_000, true)).isEqualTo(120);
        assertThat(Scoring.guesser(0, 80_000, false)).isEqualTo(100);
        assertThat(Scoring.guesser(40_000, 80_000, false)).isEqualTo(70);
        assertThat(Scoring.guesser(80_000, 80_000, false)).isEqualTo(40);
        assertThat(Scoring.guesser(95_000, 80_000, false)).isEqualTo(40);
        assertThat(Scoring.drawer(0)).isZero();
        assertThat(Scoring.drawer(3)).isEqualTo(75);
        assertThat(Scoring.drawer(9)).isEqualTo(150);
    }

    @Test
    void settingsHaveSensibleDefaults() {
        PictionaryGame game = new PictionaryGame();
        Map<String, Object> defaults =
                game.settings().stream()
                        .collect(Collectors.toMap(SettingDef::key, SettingDef::defaultValue));
        assertThat(defaults)
                .containsEntry("rounds", 2)
                .containsEntry("roundDuration", 80)
                .containsEntry("difficulty", "mixed")
                .containsEntry("hints", true);
        assertThat(game.info().id()).isEqualTo(PictionaryGame.ID);
        assertThat(game.info().minPlayers()).isEqualTo(3);
        assertThat(game.info().maxPlayers()).isEqualTo(10);
    }
}
