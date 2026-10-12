package com.example.funfridaygame.games.speedtyping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The shipped texts follow the content rules; picking never repeats a text within a game. */
class TextLibraryTest {

    @ParameterizedTest
    @EnumSource(TextLength.class)
    void everyLengthHasAtLeastThirtyValidUniqueTexts(TextLength length) {
        List<String> texts = TextLibrary.standard().texts(length);

        assertThat(texts).hasSizeGreaterThanOrEqualTo(30).doesNotHaveDuplicates();
        assertThat(texts)
                .allSatisfy(
                        text -> {
                            assertThat(TextLibrary.problems(text, length)).as(text).isEmpty();
                            assertThat(text).as("straight quotes only").doesNotContain("\"");
                            assertThat(text.length())
                                    .isLessThanOrEqualTo(
                                            SpeedTypingLogic.MAX_TYPED - SpeedTypingLogic.OVERFLOW);
                        });
    }

    @Test
    void textsAreUniqueAcrossLengths() {
        Set<String> seen = new HashSet<>();
        for (TextLength length : TextLength.values()) {
            for (String text : TextLibrary.standard().texts(length)) {
                assertThat(seen.add(text)).as(text).isTrue();
            }
        }
    }

    @Test
    void picksFreshTextsUntilAllWereUsed() {
        TextLibrary library = TextLibrary.standard();
        Random random = new Random(3);
        Set<String> used = new HashSet<>();
        int size = library.texts(TextLength.SHORT).size();

        for (int i = 0; i < size; i++) {
            assertThat(used.add(library.pick(TextLength.SHORT, used, random))).isTrue();
        }
        assertThat(library.pick(TextLength.SHORT, used, random))
                .as("all used: any text may come again")
                .isIn(used);
    }

    @Test
    void reportsProblemsWithATextAndRefusesBrokenLists() {
        assertThat(TextLibrary.problems("Too short.", TextLength.SHORT))
                .singleElement()
                .asString()
                .contains("expected 40 to 70");
        assertThat(
                        TextLibrary.problems(
                                "Curly \u201cquotes\u201d are not on every keyboard, sorry.",
                                TextLength.SHORT))
                .singleElement()
                .asString()
                .contains("ASCII");
        assertThat(
                        TextLibrary.problems(
                                "Two  spaces make the race unfair for every typist.",
                                TextLength.SHORT))
                .singleElement()
                .asString()
                .contains("double spaces");

        Map<TextLength, List<String>> broken = new EnumMap<>(TextLength.class);
        for (TextLength length : TextLength.values()) {
            broken.put(length, TextLibrary.standard().texts(length));
        }
        broken.put(TextLength.SHORT, List.of("Nope."));
        assertThatThrownBy(() -> new TextLibrary(broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nope.");
    }
}
