package com.example.funfridaygame.games.wordscramble;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Scrambling rules, guess normalisation, scoring and the miss cap. */
class WordScrambleRulesTest {

    @Test
    void rotationsAndTheReversedWordAreNotScrambles() {
        assertThat(Scrambler.isRotation("PLANET", "PLANET")).isTrue();
        assertThat(Scrambler.isRotation("PLANET", "ANETPL")).isTrue();
        assertThat(Scrambler.isRotation("PLANET", "NAPLET")).isFalse();
        assertThat(Scrambler.isGood("PLANET", "TENALP")).as("backwards").isFalse();
        assertThat(Scrambler.isGood("PLANET", "NAPLET")).isTrue();
        assertThat(Scrambler.fixedLetters("PLANET", "PNALTE")).isEqualTo(2);
    }

    @Test
    void blockedSequencesAreNeverShown() {
        assertThat(Scrambler.containsBlocked("XFARTX")).isTrue();
        assertThat(Scrambler.containsBlocked("TFRAX")).isFalse();
        assertThat(Scrambler.isGood("RAFTER", "FARTER")).isFalse();
    }

    @Test
    void scramblesAreReproducibleWithTheSameSeed() {
        assertThat(Scrambler.scramble("DOLPHIN", new Random(5)))
                .isEqualTo(Scrambler.scramble("DOLPHIN", new Random(5)))
                .isNotEqualTo("DOLPHIN");
    }

    @Test
    void guessesAreTrimmedUpperCasedAndLettersOnly() {
        assertThat(GuessText.normalize("  planet ")).isEqualTo("PLANET");
        assertThat(GuessText.normalize("Plan-et!")).isEqualTo("PLANET");
        assertThat(GuessText.normalize("CAFÉ")).isEqualTo("CAFE");
        assertThat(GuessText.normalize(" 123 ")).isEmpty();
    }

    @Test
    void pointsFollowSolveOrderSpeedAndHints() {
        long start = 0;
        long deadline = 45_000;
        assertThat(Scoring.placePoints(1)).isEqualTo(100);
        assertThat(Scoring.placePoints(2)).isEqualTo(80);
        assertThat(Scoring.placePoints(3)).isEqualTo(65);
        assertThat(Scoring.placePoints(4)).isEqualTo(50);
        assertThat(Scoring.placePoints(12)).isEqualTo(50);

        assertThat(Scoring.speedBonus(start, start, deadline)).isEqualTo(20);
        assertThat(Scoring.speedBonus(22_500, start, deadline)).isEqualTo(10);
        assertThat(Scoring.speedBonus(deadline, start, deadline)).isZero();
        assertThat(Scoring.speedBonus(deadline + 5, start, deadline)).isZero();

        assertThat(Scoring.points(1, 22_500, start, deadline, 0)).isEqualTo(110);
        assertThat(Scoring.points(1, 22_500, start, deadline, 1)).isEqualTo(83);
        assertThat(Scoring.points(4, deadline, start, deadline, 2)).isEqualTo(25);
    }

    @Test
    void theMissCapNeverAllowsTryingAQuarterOfAllOrders() {
        assertThat(WordScrambleLogic.maxMisses(4)).isEqualTo(6);
        assertThat(WordScrambleLogic.maxMisses(5)).isEqualTo(30);
        assertThat(WordScrambleLogic.maxMisses(6)).isEqualTo(40);
        assertThat(WordScrambleLogic.maxMisses(8)).isEqualTo(40);
    }

    @Test
    void mixedLengthsStayBetweenFiveAndSeven() {
        Random random = new Random(1);
        WordLength mixed = WordLength.parse(WordLength.MIXED);
        for (int i = 0; i < 200; i++) {
            assertThat(mixed.next(random)).isBetween(5, 7);
        }
        assertThat(WordLength.parse("8").next(random)).isEqualTo(8);
    }
}
