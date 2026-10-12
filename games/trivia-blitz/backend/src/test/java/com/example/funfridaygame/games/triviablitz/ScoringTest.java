package com.example.funfridaygame.games.triviablitz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The scoring formula. */
class ScoringTest {

    private static final long FIFTEEN_SECONDS = 15_000;

    @Test
    void speedPointsSlideFrom1000To500() {
        assertThat(Scoring.speedPoints(0, FIFTEEN_SECONDS)).isEqualTo(1000);
        assertThat(Scoring.speedPoints(7_500, FIFTEEN_SECONDS)).isEqualTo(750);
        assertThat(Scoring.speedPoints(15_000, FIFTEEN_SECONDS)).isEqualTo(500);
        assertThat(Scoring.speedPoints(20_000, FIFTEEN_SECONDS))
                .as("never below 500")
                .isEqualTo(500);
        assertThat(Scoring.speedPoints(-5, FIFTEEN_SECONDS)).as("never above 1000").isEqualTo(1000);
    }

    @Test
    void speedPointsAreRoundedToTens() {
        // 1234 ms of 15 s: 1000 − 500 × 0.0823 = 958.9 → 960
        assertThat(Scoring.speedPoints(1_234, FIFTEEN_SECONDS)).isEqualTo(960);
        // 2 s of 15 s: 933.3 → 930
        assertThat(Scoring.speedPoints(2_000, FIFTEEN_SECONDS)).isEqualTo(930);
        // 3 s of 10 s: exactly 850
        assertThat(Scoring.speedPoints(3_000, 10_000)).isEqualTo(850);
    }

    @Test
    void theStreakBonusGrowsBy100AndStopsAt300() {
        assertThat(Scoring.streakBonus(1)).isZero();
        assertThat(Scoring.streakBonus(2)).isEqualTo(100);
        assertThat(Scoring.streakBonus(3)).isEqualTo(200);
        assertThat(Scoring.streakBonus(4)).isEqualTo(300);
        assertThat(Scoring.streakBonus(9)).isEqualTo(300);
    }

    @Test
    void pointsCombineSpeedAndStreak() {
        assertThat(Scoring.points(7_500, FIFTEEN_SECONDS, 3)).isEqualTo(950);
    }
}
