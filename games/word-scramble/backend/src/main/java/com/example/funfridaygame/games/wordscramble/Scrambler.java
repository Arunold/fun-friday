package com.example.funfridaygame.games.wordscramble;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Shuffles a word into the letters everybody sees. A scramble is accepted only when it
 *
 * <ul>
 *   <li>is not the word itself, not one of its rotations ({@code PLANET → ANETPL}) and not the word
 *       backwards,
 *   <li>keeps at most a third of the letters in their original place, and
 *   <li>never shows a blocked letter sequence ({@code word-scramble/blocked.txt}), so an innocent
 *       word can't turn into something rude on screen.
 * </ul>
 *
 * <p>It re-shuffles until every rule holds. Should a word allow no such scramble (none of the
 * curated words does — a test checks them all), the place rule and then the remaining rules are
 * relaxed, so a round always starts with letters that differ from the answer.
 */
final class Scrambler {

    /** Shuffles tried per rule level before relaxing. */
    static final int ATTEMPTS = 400;

    private Scrambler() {}

    /** The blocked sequences, decoded on first use. */
    private static final class Blocked {
        private static final List<String> SEQUENCES =
                ResourceLines.read("/word-scramble/blocked.txt").stream()
                        .map(Scrambler::rot13)
                        .toList();
    }

    /**
     * Scrambles a word.
     *
     * @param word upper-case word with at least two different letters
     * @param random the game's random source
     */
    static String scramble(String word, RandomGenerator random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String candidate = shuffle(word, random);
            if (isGood(word, candidate) && fixedLetters(word, candidate) <= word.length() / 3) {
                return candidate;
            }
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String candidate = shuffle(word, random);
            if (isGood(word, candidate)) {
                return candidate;
            }
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String candidate = shuffle(word, random);
            if (!candidate.equals(word)) {
                return candidate;
            }
        }
        return word.substring(1) + word.charAt(0);
    }

    /** Not the word, a rotation of it or the word backwards, and nothing blocked. */
    static boolean isGood(String word, String candidate) {
        return !isRotation(word, candidate)
                && !candidate.equals(new StringBuilder(word).reverse().toString())
                && !containsBlocked(candidate);
    }

    /** Whether {@code candidate} is {@code word} or one of its rotations. */
    static boolean isRotation(String word, String candidate) {
        return word.length() == candidate.length() && (word + word).contains(candidate);
    }

    /** How many positions show the same letter in both strings. */
    static int fixedLetters(String word, String candidate) {
        int fixed = 0;
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == candidate.charAt(i)) {
                fixed++;
            }
        }
        return fixed;
    }

    /** Whether the letters contain a blocked sequence. */
    static boolean containsBlocked(String letters) {
        for (String sequence : Blocked.SEQUENCES) {
            if (letters.contains(sequence)) {
                return true;
            }
        }
        return false;
    }

    /** Fisher–Yates shuffle of the word's letters. */
    private static String shuffle(String word, RandomGenerator random) {
        char[] letters = word.toCharArray();
        for (int i = letters.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = letters[i];
            letters[i] = letters[j];
            letters[j] = swap;
        }
        return new String(letters);
    }

    private static String rot13(String text) {
        StringBuilder decoded = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            decoded.append(c >= 'A' && c <= 'Z' ? (char) ('A' + (c - 'A' + 13) % 26) : c);
        }
        return decoded.toString();
    }
}
