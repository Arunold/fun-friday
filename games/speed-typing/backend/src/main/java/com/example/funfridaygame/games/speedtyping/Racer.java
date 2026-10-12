package com.example.funfridaygame.games.speedtyping;

/**
 * One player's race in the current turn, built from their progress updates.
 *
 * <p>The server is authoritative for everything it can check: the typed text decides the progress
 * (only the correctly typed prefix counts, so mashing keys earns nothing) and the finish (the text
 * must match exactly). Keystroke and error counts can only be measured in the browser; they are
 * accepted but clamped to what the typed text and the elapsed time make possible.
 *
 * <p>Speed check: the typed text may grow by at most {@value #MAX_CHARS_PER_SECOND} characters per
 * second, with a burst allowance of {@value #BURST_CHARS} characters for updates that arrive
 * bunched up after a network hiccup (a token bucket). Faster updates are ignored rather than
 * rejected: the browser keeps re-sending its full text, so a delayed legitimate update lands a
 * moment later, while a pasted text never gets through.
 */
final class Racer {

    /** Fastest sustained typing accepted, in characters per second (300 WPM). */
    static final int MAX_CHARS_PER_SECOND = 25;

    /** Characters that may arrive at once on top of the sustained rate. */
    static final int BURST_CHARS = 15;

    private static final double CHARS_PER_WORD = 5;
    private static final long MIN_TYPING_MILLIS = 1000;

    /** What a progress update did. */
    enum Update {
        /** The typed text changed. */
        CHANGED,
        /** Nothing others can see changed (same text, or the race is already over). */
        UNCHANGED,
        /** Ignored: the text grew faster than anyone can type. */
        TOO_FAST,
        /** The text now matches exactly. */
        FINISHED
    }

    private final long turnStartedAt;
    private String typed = "";
    private int correct;
    private int matching;
    private int keystrokes;
    private int errors;
    private long firstInputAt = -1;
    private long finishedAt = -1;
    private int rank;
    private double allowance = BURST_CHARS;
    private long allowanceAt;

    /** A racer that hasn't typed yet in the turn that started at {@code turnStartedAt}. */
    Racer(long turnStartedAt) {
        this.turnStartedAt = turnStartedAt;
        this.allowanceAt = turnStartedAt;
    }

    /**
     * Applies a progress update.
     *
     * @param text the text everybody types this turn
     * @param newTyped everything the player has typed so far
     * @param reportedKeystrokes characters typed in total, including corrected ones (browser count)
     * @param reportedErrors characters that were wrong when typed (browser count)
     * @param now server time of the update
     * @return what changed
     */
    Update apply(
            String text, String newTyped, int reportedKeystrokes, int reportedErrors, long now) {
        if (finished()) {
            return Update.UNCHANGED;
        }
        refill(now);
        int newCorrect = commonPrefix(text, newTyped);
        int growth =
                Math.max(0, Math.max(newTyped.length() - typed.length(), newCorrect - correct));
        if (growth > allowance) {
            return Update.TOO_FAST;
        }
        allowance -= growth;
        boolean changed = !newTyped.equals(typed);
        typed = newTyped;
        correct = newCorrect;
        matching = matchingChars(text, newTyped);
        count(reportedKeystrokes, reportedErrors, now);
        if (firstInputAt < 0 && keystrokes > 0) {
            firstInputAt = now;
        }
        if (typed.equals(text)) {
            finishedAt = now;
            return Update.FINISHED;
        }
        return changed ? Update.CHANGED : Update.UNCHANGED;
    }

    /**
     * Takes the browser's keystroke and error counts, clamped: both only grow; every character that
     * is currently wrong was an error; correct characters plus errors were all keystrokes; and
     * nobody types faster than {@value #MAX_CHARS_PER_SECOND} characters per second.
     */
    private void count(int reportedKeystrokes, int reportedErrors, long now) {
        int present = matching;
        int wrong = typed.length() - present;
        long elapsed = Math.max(0, now - turnStartedAt);
        int timeCap = (int) Math.min(Integer.MAX_VALUE, elapsed * MAX_CHARS_PER_SECOND / 1000);
        int cap = Math.max(Math.max(keystrokes, typed.length()), timeCap + BURST_CHARS);
        int newErrors = clamp(Math.max(reportedErrors, errors), wrong, cap - present);
        keystrokes = clamp(Math.max(reportedKeystrokes, keystrokes), newErrors + present, cap);
        errors = newErrors;
    }

    private void refill(long now) {
        if (now > allowanceAt) {
            double earned = (now - allowanceAt) * MAX_CHARS_PER_SECOND / 1000.0;
            allowance = Math.min(BURST_CHARS, allowance + earned);
            allowanceAt = now;
        }
    }

    // ------------------------------------------------------------------ results

    /** Whether the player typed the text exactly. */
    boolean finished() {
        return finishedAt >= 0;
    }

    /** Finishing place (1 = first), 0 while not finished. */
    int rank() {
        return rank;
    }

    /** Records the finishing place. */
    void setRank(int rank) {
        this.rank = rank;
    }

    /** Everything the player typed (never shown to others). */
    String typed() {
        return typed;
    }

    /** Length of the correctly typed prefix. */
    int correct() {
        return correct;
    }

    /** Characters typed in total, including corrected ones. */
    int keystrokes() {
        return keystrokes;
    }

    /** Characters that were wrong when typed. */
    int errors() {
        return errors;
    }

    /** Whether the player pressed any key this turn. */
    boolean typedAnything() {
        return keystrokes > 0;
    }

    /** Server time of the first keystroke, {@code null} before it. */
    Long startedAt() {
        return firstInputAt < 0 ? null : firstInputAt;
    }

    /** Time from the start of the turn to the finish, {@code null} while not finished. */
    Long timeMillis() {
        return finished() ? finishedAt - turnStartedAt : null;
    }

    /** Progress in percent from the correct prefix; 100 only once finished. */
    int progress(int textLength) {
        return finished() ? 100 : Math.min(99, correct * 100 / Math.max(1, textLength));
    }

    /** Correct keystrokes ÷ all keystrokes (1 before the first keystroke). */
    double accuracy() {
        return keystrokes == 0 ? 1.0 : (double) (keystrokes - errors) / keystrokes;
    }

    /** {@link #accuracy()} in whole percent, rounded down (99.6 % shows as 99 %). */
    int accuracyPercent() {
        return keystrokes == 0 ? 100 : (keystrokes - errors) * 100 / keystrokes;
    }

    /** Whether the accuracy is below {@code minPercent} (exact, without rounding). */
    boolean belowAccuracy(int minPercent) {
        return (long) (keystrokes - errors) * 100 < (long) minPercent * keystrokes;
    }

    /**
     * Words per minute from the first keystroke (5 characters = 1 word), counting the characters
     * that are right where they are: up to the finish, or up to {@code turnEnd} for players who
     * didn't finish. A single early typo therefore doesn't wipe out the speed of a non-finisher.
     */
    double wpm(long turnEnd) {
        if (firstInputAt < 0) {
            return 0;
        }
        long end = finished() ? finishedAt : turnEnd;
        long millis = Math.max(MIN_TYPING_MILLIS, end - firstInputAt);
        return matching / CHARS_PER_WORD / (millis / 60_000.0);
    }

    // ------------------------------------------------------------------ helpers

    /** Length of the common prefix of the text and the typed text. */
    static int commonPrefix(String text, String typed) {
        int max = Math.min(text.length(), typed.length());
        int i = 0;
        while (i < max && text.charAt(i) == typed.charAt(i)) {
            i++;
        }
        return i;
    }

    /** Typed characters that match the text at their position. */
    static int matchingChars(String text, String typed) {
        int max = Math.min(text.length(), typed.length());
        int matching = 0;
        for (int i = 0; i < max; i++) {
            if (text.charAt(i) == typed.charAt(i)) {
                matching++;
            }
        }
        return matching;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
