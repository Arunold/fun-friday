package com.example.funfridaygame.games.speedtyping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.funfridaygame.games.speedtyping.Racer.Update;
import org.junit.jupiter.api.Test;

/** The per-player race state: what the server trusts, clamps and ignores. */
class RacerTest {

    private static final String TEXT = "The quick brown fox jumps over the lazy dog near the park.";

    @Test
    void progressAndTheFinishComeFromTheTypedText() {
        Racer racer = new Racer(0);

        assertThat(racer.apply(TEXT, "The quack", 9, 1, 1_000)).isEqualTo(Update.CHANGED);
        assertThat(racer.correct()).isEqualTo(6);
        assertThat(racer.progress(TEXT.length())).isEqualTo(6 * 100 / TEXT.length());
        assertThat(racer.finished()).isFalse();
        assertThat(racer.apply(TEXT, "The quack", 10, 2, 1_100)).isEqualTo(Update.UNCHANGED);
    }

    @Test
    void growthBeyondTwentyFiveCharactersPerSecondPlusABurstIsIgnored() {
        Racer racer = new Racer(0);

        assertThat(racer.apply(TEXT, TEXT, TEXT.length(), 0, 0)).isEqualTo(Update.TOO_FAST);
        assertThat(racer.apply(TEXT, TEXT.substring(0, 15), 15, 0, 0)).isEqualTo(Update.CHANGED);
        assertThat(racer.apply(TEXT, TEXT.substring(0, 20), 20, 0, 100)).isEqualTo(Update.TOO_FAST);
        assertThat(racer.apply(TEXT, TEXT.substring(0, 20), 20, 0, 200)).isEqualTo(Update.CHANGED);
        assertThat(racer.typed()).hasSize(20);
    }

    @Test
    void replacingWrongCharactersCostsLikeTypingThem() {
        Racer racer = new Racer(0);
        String wrong = "x".repeat(15);
        assertThat(racer.apply(TEXT, wrong, 15, 15, 0)).isEqualTo(Update.CHANGED);

        assertThat(racer.apply(TEXT, TEXT.substring(0, 15), 30, 15, 10))
                .as("same length, but 15 more correct characters at once")
                .isEqualTo(Update.TOO_FAST);
        assertThat(racer.apply(TEXT, TEXT.substring(0, 15), 30, 15, 700)).isEqualTo(Update.CHANGED);
    }

    @Test
    void countsAreMonotonicAndConsistentWithTheTypedText() {
        Racer racer = new Racer(0);

        racer.apply(TEXT, "Thx", 0, 0, 2_000);
        assertThat(racer.errors()).as("the x is wrong").isEqualTo(1);
        assertThat(racer.keystrokes()).as("two correct and one wrong").isEqualTo(3);

        racer.apply(TEXT, "Th", 2, 0, 2_100);
        assertThat(racer.keystrokes()).as("never shrinks").isEqualTo(3);
        assertThat(racer.errors()).isEqualTo(1);

        racer.apply(TEXT, "The", 1_000, 1, 2_200);
        assertThat(racer.keystrokes()).as("at most 25/s + burst").isEqualTo(2_200 * 25 / 1000 + 15);
    }

    @Test
    void speedAndAccuracyAreMeasuredFromTheFirstKeystroke() {
        Racer racer = new Racer(0);
        racer.apply(TEXT, "T", 1, 0, 5_000);
        for (int chars = 2; chars <= TEXT.length(); chars++) {
            racer.apply(TEXT, TEXT.substring(0, chars), chars + 2, 2, 5_000 + chars * 200L);
        }

        assertThat(racer.finished()).isTrue();
        assertThat(racer.startedAt()).isEqualTo(5_000L);
        assertThat(racer.timeMillis()).isEqualTo(5_000L + TEXT.length() * 200L);
        double minutes = (TEXT.length() * 200.0) / 60_000;
        assertThat(racer.wpm(0)).isCloseTo(TEXT.length() / 5.0 / minutes, within(1e-9));
        assertThat(racer.accuracy()).isCloseTo(TEXT.length() / (TEXT.length() + 2.0), within(1e-9));
        assertThat(racer.accuracyPercent()).isEqualTo(TEXT.length() * 100 / (TEXT.length() + 2));
        assertThat(racer.belowAccuracy(95)).isFalse();
        assertThat(racer.belowAccuracy(97)).isTrue();
        assertThat(racer.apply(TEXT, "late", 99, 0, 99_000)).isEqualTo(Update.UNCHANGED);
    }

    @Test
    void anEarlyTypoDoesNotWipeOutTheSpeedOfANonFinisher() {
        Racer racer = new Racer(0);
        racer.apply(TEXT, "T", 1, 0, 0);
        racer.apply(TEXT, "Thx quick brown", 15, 1, 600);

        assertThat(racer.correct()).as("progress stops at the typo").isEqualTo(2);
        assertThat(racer.wpm(60_000)).as("14 characters in a minute").isCloseTo(2.8, within(1e-9));
    }

    @Test
    void helpersCompareTheTextCharacterByCharacter() {
        assertThat(Racer.commonPrefix("hello", "help")).isEqualTo(3);
        assertThat(Racer.commonPrefix("hello", "hello world")).isEqualTo(5);
        assertThat(Racer.matchingChars("hello", "hxllo")).isEqualTo(4);
        assertThat(new Racer(0).accuracyPercent()).isEqualTo(100);
        assertThat(new Racer(0).wpm(60_000)).isZero();
    }
}
