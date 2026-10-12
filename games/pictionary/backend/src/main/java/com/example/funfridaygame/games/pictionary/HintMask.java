package com.example.funfridaygame.games.pictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The secret word as guessers see it: letters are shown as {@code _} until a hint reveals them;
 * spaces and hyphens stay visible so the shape of the word is known from the start.
 *
 * <p>Hints reveal a third of the letters (at most {@value #MAX_HINT_LETTERS}) in random order,
 * split over the two hint moments, so at least two thirds of a word always stay hidden.
 */
final class HintMask {

    /** Shown instead of a hidden letter. */
    static final char HIDDEN = '_';

    /** Letters revealed by both hints together, at most. */
    static final int MAX_HINT_LETTERS = 4;

    private final String word;
    private final boolean[] shown;
    private final List<Integer> order;
    private int revealed;

    /** A mask with every letter hidden; {@code random} decides the reveal order. */
    HintMask(String word, RandomGenerator random) {
        this.word = word;
        this.shown = new boolean[word.length()];
        List<Integer> letters = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                letters.add(i);
            }
        }
        for (int i = letters.size() - 1; i > 0; i--) {
            Collections.swap(letters, i, random.nextInt(i + 1));
        }
        this.order = letters;
    }

    /** Number of letters in the word. */
    int letters() {
        return order.size();
    }

    /** Letters revealed by the hint at half time. */
    int firstHintLetters() {
        return totalHintLetters() / 2;
    }

    /** Letters revealed by the hint at three quarters of the time. */
    int secondHintLetters() {
        return totalHintLetters() - firstHintLetters();
    }

    /** Reveals up to {@code count} more letters. */
    void reveal(int count) {
        for (int i = 0; i < count && revealed < order.size(); i++) {
            shown[order.get(revealed++)] = true;
        }
    }

    /** Number of letters revealed so far. */
    int revealed() {
        return revealed;
    }

    /** The word with hidden letters replaced by {@value #HIDDEN}, e.g. {@code "_c_ cr__m"}. */
    String masked() {
        StringBuilder text = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            text.append(Character.isLetter(c) && !shown[i] ? HIDDEN : c);
        }
        return text.toString();
    }

    private int totalHintLetters() {
        return Math.min(MAX_HINT_LETTERS, letters() / 3);
    }
}
