package com.example.funfridaygame.games.speedtyping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The scoring table from the README, one formula at a time. */
class ScoringTest {

    @ParameterizedTest
    @CsvSource({"1, 100", "2, 80", "3, 65", "4, 55", "5, 50", "6, 50", "20, 50"})
    void placesScoreByFinishingOrder(int rank, int points) {
        assertThat(Scoring.placePoints(rank)).isEqualTo(points);
    }

    @Test
    void theSpeedBonusIsAQuarterOfTheWpmUpTo120() {
        assertThat(Scoring.speedBonus(60)).isEqualTo(15);
        assertThat(Scoring.speedBonus(120)).isEqualTo(30);
        assertThat(Scoring.speedBonus(250)).isEqualTo(30);
        assertThat(Scoring.speedBonus(-5)).isZero();
    }

    @Test
    void finishersAreMultipliedByTheirAccuracyAndHalvedBelowTheMinimum() {
        assertThat(Scoring.finisher(1, 80, 1.0, false)).isEqualTo(120);
        assertThat(Scoring.finisher(1, 80, 0.98, false)).isEqualTo(118);
        assertThat(Scoring.finisher(2, 60, 1.0, false)).isEqualTo(95);
        assertThat(Scoring.finisher(3, 140, 0.85, true)).isEqualTo(40);
        assertThat(Scoring.finisher(7, 40, 0.9, false)).isEqualTo(54);
    }

    @Test
    void unfinishedPlayersGetUpToFortyByProgress() {
        assertThat(Scoring.unfinished(0, 60)).isZero();
        assertThat(Scoring.unfinished(30, 60)).isEqualTo(20);
        assertThat(Scoring.unfinished(59, 60)).isEqualTo(39);
        assertThat(Scoring.unfinished(80, 60)).isEqualTo(40);
        assertThat(Scoring.unfinished(5, 0)).isZero();
    }
}
