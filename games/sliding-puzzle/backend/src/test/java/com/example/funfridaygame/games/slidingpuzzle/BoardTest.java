package com.example.funfridaygame.games.slidingpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The board model, the scrambler and the scoring formulas. */
class BoardTest {

    @Test
    void theSolvedBoardHasEveryTileHomeAndTheGapBottomRight() {
        Board board = Board.solved(3);

        assertThat(board.cells()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 0);
        assertThat(board.isSolved()).isTrue();
        assertThat(board.tilesInPlace()).isEqualTo(8);
        assertThat(board.manhattanDistance()).isZero();
        assertThat(board.gapIndex()).isEqualTo(8);
    }

    @Test
    void pushingATileSlidesEveryTileBetweenItAndTheGap() {
        Board board = Board.solved(4);

        assertThat(board.slide(13)).as("three tiles along the bottom row").isEqualTo(3);
        assertThat(board.cells())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0, 13, 14, 15);

        assertThat(board.slide(1)).as("three tiles up the first column").isEqualTo(3);
        assertThat(board.cells())
                .containsExactly(0, 2, 3, 4, 1, 6, 7, 8, 5, 10, 11, 12, 9, 13, 14, 15);
        assertThat(board.tilesInPlace()).isEqualTo(9);
        assertThat(board.manhattanDistance()).isEqualTo(6);
    }

    @Test
    void tilesOutOfLineWithTheGapDoNotMove() {
        Board board = Board.solved(3);

        assertThat(board.slide(1)).isZero();
        assertThat(board.slide(0)).as("the gap itself").isZero();
        assertThat(board.slide(9)).as("no such tile").isZero();
        assertThat(board.isSolved()).isTrue();
    }

    @Test
    void movesIntoTheGapOnlyFromNeighbouringCells() {
        Board board = Board.solved(3);

        assertThat(board.gapNeighbours()).containsExactlyInAnyOrder(5, 7);
        assertThatThrownBy(() -> board.moveIntoGap(0)).isInstanceOf(IllegalArgumentException.class);
        board.moveIntoGap(7);
        assertThat(board.cells()).containsExactly(1, 2, 3, 4, 5, 6, 7, 0, 8);
        assertThat(board.gapNeighbours()).containsExactlyInAnyOrder(4, 6, 8);
    }

    @Test
    void rejectsBoardsThatAreNoPermutation() {
        assertThatThrownBy(() -> Board.of(2, 1, 1, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Board.of(2, 1, 2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Board.of(2, 3, 1, 2, 0).copy().cells()).containsExactly(3, 1, 2, 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void scramblesAreSolvableHardEnoughAndNeverSolved(int size) {
        for (long seed = 1; seed <= 300; seed++) {
            Board board = Scrambler.scramble(size, new SplittableRandom(seed));

            assertThat(isSolvable(board)).as("seed %d solvable", seed).isTrue();
            assertThat(board.isSolved()).isFalse();
            assertThat(board.manhattanDistance())
                    .isGreaterThanOrEqualTo(Scrambler.minimumDistance(size));
            assertThat(board.misplacedTiles() * 10).isGreaterThanOrEqualTo(board.tileCount() * 7);
        }
    }

    @Test
    void theMinimumScrambleDistanceGrowsWithTheBoard() {
        assertThat(Scrambler.minimumDistance(3)).isEqualTo(12);
        assertThat(Scrambler.minimumDistance(4)).isEqualTo(32);
        assertThat(Scrambler.minimumDistance(5)).isEqualTo(65);
        assertThat(Scrambler.isHardEnough(Board.solved(4))).isFalse();
    }

    @Test
    void scoringFormulas() {
        assertThat(Scoring.placePoints(1)).isEqualTo(100);
        assertThat(Scoring.placePoints(2)).isEqualTo(80);
        assertThat(Scoring.placePoints(3)).isEqualTo(65);
        assertThat(Scoring.placePoints(4)).isEqualTo(55);
        assertThat(Scoring.placePoints(5)).isEqualTo(50);
        assertThat(Scoring.placePoints(9)).isEqualTo(50);

        assertThat(Scoring.efficiencyBonus(20, 40)).as("at most twice the distance").isEqualTo(30);
        assertThat(Scoring.efficiencyBonus(20, 25)).isEqualTo(30);
        assertThat(Scoring.efficiencyBonus(20, 80)).as("twice as many moves").isEqualTo(15);
        assertThat(Scoring.efficiencyBonus(20, 400)).isEqualTo(3);

        assertThat(Scoring.progressPoints(15, 15)).isEqualTo(40);
        assertThat(Scoring.progressPoints(6, 8)).isEqualTo(30);
        assertThat(Scoring.progressPoints(0, 24)).isZero();

        assertThat(Scoring.formatTime(42_349)).isEqualTo("42.3 s");
        assertThat(Scoring.formatTime(125_000)).isEqualTo("2:05");
    }

    /** Inversion-count test, independent of how the scramble was made. */
    private static boolean isSolvable(Board board) {
        int size = board.size();
        int inversions = 0;
        int cells = size * size;
        for (int i = 0; i < cells; i++) {
            for (int j = i + 1; j < cells; j++) {
                int a = board.tileAt(i);
                int b = board.tileAt(j);
                if (a != 0 && b != 0 && a > b) {
                    inversions++;
                }
            }
        }
        if (size % 2 == 1) {
            return inversions % 2 == 0;
        }
        int gapRowFromBottom = size - board.gapIndex() / size;
        return (inversions + gapRowFromBottom) % 2 == 1;
    }
}
