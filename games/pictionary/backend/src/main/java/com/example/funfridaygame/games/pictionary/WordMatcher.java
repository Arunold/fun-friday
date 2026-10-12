package com.example.funfridaygame.games.pictionary;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Judges guesses. Only exact matches score (IMP-20: no partial credit); a near miss — one typo in a
 * word of at least {@value #CLOSE_MIN_LETTERS} letters, or the word inside a longer guess — earns a
 * private "So close!" instead and is hidden from the other players because it would give the word
 * away.
 */
final class WordMatcher {

    /** Shortest word for which a one-typo guess counts as close. */
    static final int CLOSE_MIN_LETTERS = 5;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9' -]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern JOINERS = Pattern.compile("[ '-]");

    /** How a guess compares to the word. */
    enum Verdict {
        CORRECT,
        CLOSE,
        WRONG
    }

    private WordMatcher() {}

    /** Compares a guess with the secret word. */
    static Verdict judge(String guess, String word) {
        String normalizedGuess = normalize(guess);
        String normalizedWord = normalize(word);
        String compactGuess = compact(normalizedGuess);
        String compactWord = compact(normalizedWord);
        if (compactGuess.equals(compactWord)) {
            return Verdict.CORRECT;
        }
        if (compactWord.length() >= CLOSE_MIN_LETTERS && distance(compactGuess, compactWord) <= 1) {
            return Verdict.CLOSE;
        }
        if ((" " + normalizedGuess + " ").contains(" " + normalizedWord + " ")) {
            return Verdict.CLOSE;
        }
        return Verdict.WRONG;
    }

    /** Lower case without accents or punctuation, single spaces, trimmed. */
    static String normalize(String text) {
        String plain =
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String cleaned = NOT_ALLOWED.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return SPACES.matcher(cleaned).replaceAll(" ").strip();
    }

    /** A normalized text without spaces, hyphens and apostrophes: "ice-cream" = "icecream". */
    static String compact(String normalized) {
        return JOINERS.matcher(normalized).replaceAll("");
    }

    /**
     * Optimal string alignment distance (insertions, deletions, substitutions and swaps of
     * neighbouring letters each count 1), capped at 2 since only "at most one" matters.
     */
    static int distance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return 2;
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best =
                        Math.min(
                                Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1
                        && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, d[i - 2][j - 2] + 1);
                }
                d[i][j] = best;
            }
        }
        return Math.min(2, d[a.length()][b.length()]);
    }
}
