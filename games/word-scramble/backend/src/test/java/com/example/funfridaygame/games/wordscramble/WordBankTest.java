package com.example.funfridaygame.games.wordscramble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The bundled word lists, their validation and the scrambler on every curated word. */
class WordBankTest {

    @Test
    void everyLengthHasEnoughValidWords() {
        for (int length = WordBank.MIN_LENGTH; length <= WordBank.MAX_LENGTH; length++) {
            List<String> words = WordBank.words(length);
            assertThat(words)
                    .hasSizeGreaterThanOrEqualTo(WordBank.MIN_WORDS)
                    .doesNotHaveDuplicates();
            int expected = length;
            assertThat(words).allMatch(w -> w.length() == expected && w.matches("[A-Z]+"));
        }
    }

    @Test
    void noTwoWordsAreAnagramsOfEachOther() {
        Map<String, String> bySignature = new HashMap<>();
        for (int length = WordBank.MIN_LENGTH; length <= WordBank.MAX_LENGTH; length++) {
            for (String word : WordBank.words(length)) {
                assertThat(bySignature.put(WordBank.signature(word), word))
                        .as("anagram twin of %s", word)
                        .isNull();
            }
        }
    }

    @Test
    void validationListsEveryProblem() {
        List<String> words = List.of("TIGER", "tiger", "LION", "TIGER", "TIGRE");

        assertThatThrownBy(() -> WordBank.validate(words, 5, "test.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.txt")
                .hasMessageContaining("tiger must be upper-case")
                .hasMessageContaining("LION doesn't have 5 letters")
                .hasMessageContaining("TIGER is listed twice")
                .hasMessageContaining("TIGRE is an anagram of TIGER")
                .hasMessageContaining("at least 60 words");
    }

    @Test
    void picksUnusedWordsOfTheRequestedLength() {
        Random random = new Random(3);
        Set<String> used = new HashSet<>();
        for (int i = 0; i < WordBank.words(4).size(); i++) {
            String word = WordBank.pick(4, random, used);
            assertThat(used.add(word)).as("%s drawn twice", word).isTrue();
        }
        assertThat(WordBank.pick(4, random, used)).as("exhausted: any word").hasSize(4);
        assertThatThrownBy(() -> WordBank.words(9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyCuratedWordGetsAProperScramble() {
        Random random = new Random(11);
        for (int length = WordBank.MIN_LENGTH; length <= WordBank.MAX_LENGTH; length++) {
            for (String word : WordBank.words(length)) {
                for (int run = 0; run < 5; run++) {
                    String scramble = Scrambler.scramble(word, random);
                    assertThat(WordBank.signature(scramble)).isEqualTo(WordBank.signature(word));
                    assertThat(Scrambler.isGood(word, scramble))
                            .as("%s → %s", word, scramble)
                            .isTrue();
                    assertThat(Scrambler.fixedLetters(word, scramble))
                            .as("%s → %s keeps too many letters in place", word, scramble)
                            .isLessThanOrEqualTo(length / 3);
                }
            }
        }
    }
}
