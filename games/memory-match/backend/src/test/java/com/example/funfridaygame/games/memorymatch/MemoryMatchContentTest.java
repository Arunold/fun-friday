package com.example.funfridaygame.games.memorymatch;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.runtime.InvalidSettingsException;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Content and settings: emoji themes, dealing, board sizes and setting validation. */
class MemoryMatchContentTest {

    @ParameterizedTest
    @EnumSource(CardTheme.class)
    void everyThemeHasEnoughDistinctEmojiThatRenderTheSameEverywhere(CardTheme theme) {
        List<String> faces = theme.faces();

        assertThat(faces).doesNotHaveDuplicates().hasSizeGreaterThanOrEqualTo(18);
        assertThat(faces)
                .allSatisfy(
                        face -> {
                            // One code point: no flags, ZWJ sequences, skin tones or selectors.
                            assertThat(face.codePointCount(0, face.length())).isEqualTo(1);
                            assertThat(Character.getType(face.codePointAt(0)))
                                    .isEqualTo((int) Character.OTHER_SYMBOL);
                        });
    }

    @Test
    void mixedCombinesEveryThemeWithoutDuplicates() {
        List<String> all =
                Arrays.stream(CardTheme.values())
                        .filter(theme -> theme != CardTheme.MIXED)
                        .flatMap(theme -> theme.faces().stream())
                        .toList();

        assertThat(all).doesNotHaveDuplicates();
        assertThat(CardTheme.MIXED.faces()).containsExactlyInAnyOrderElementsOf(all);
    }

    @ParameterizedTest
    @EnumSource(BoardSize.class)
    void everyBoardHoldsEachFaceExactlyTwice(BoardSize size) {
        for (CardTheme theme : CardTheme.values()) {
            Board board = Board.deal(size, theme, new Random(7));

            Map<String, Long> counts =
                    board.faces().stream().collect(groupingBy(identity(), counting()));
            assertThat(board.faces()).hasSize(size.rows() * size.cols());
            assertThat(counts).hasSize(size.pairs());
            assertThat(counts.values()).containsOnly(2L);
            assertThat(theme.faces()).containsAll(counts.keySet());
            assertThat(board.cardViews(false))
                    .allSatisfy(card -> assertThat(card.emoji()).isNull());
        }
    }

    @Test
    void theChosenBoardAndThemeAreUsed() {
        GameHarness game =
                GameHarness.of(new MemoryMatchGame())
                        .players("alice", "bob")
                        .settings(Map.of("board", "6x6", "theme", "animals", "moveTime", 5))
                        .seed(3)
                        .start();

        assertThat(game.view("alice"))
                .containsEntry("rows", 6)
                .containsEntry("cols", 6)
                .containsEntry("pairsTotal", 18)
                .containsEntry("moveTimeMs", 5_000L);
        @SuppressWarnings("unchecked")
        List<String> faces = (List<String>) game.debugState().get("faces");
        assertThat(CardTheme.ANIMALS.faces()).containsAll(faces);
    }

    @Test
    void rejectsValuesThatAreNotOffered() {
        assertThatThrownBy(() -> start(Map.of("board", "5x5")))
                .isInstanceOf(InvalidSettingsException.class);
        assertThatThrownBy(() -> start(Map.of("moveTime", 7)))
                .isInstanceOf(InvalidSettingsException.class);
        assertThatThrownBy(() -> start(Map.of("theme", "flags")))
                .isInstanceOf(InvalidSettingsException.class);
    }

    @Test
    void theRoundSafetyNetNeverExceedsTenMinutes() {
        MemoryMatchLogic slow =
                new MemoryMatchLogic(BoardSize.HUGE, Duration.ofSeconds(60), CardTheme.MIXED);

        assertThat(slow.roundLength()).isEqualTo(MemoryMatchLogic.MAX_ROUND);
    }

    private static GameHarness start(Map<String, Object> settings) {
        return GameHarness.of(new MemoryMatchGame())
                .players("alice", "bob")
                .settings(settings)
                .start();
    }
}
