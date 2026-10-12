package com.example.funfridaygame.games.triviablitz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * One multiple-choice question. The constructor enforces the bank's rules: exactly four distinct,
 * non-blank options, a valid answer index and lengths that fit the answer buttons.
 *
 * @param category the category
 * @param difficulty how hard it is
 * @param text the question
 * @param options exactly {@value #OPTIONS} distinct answers
 * @param answer index of the correct option in {@code options}
 * @param fact an optional one-line fun fact shown at the reveal ({@code null} if none)
 */
record Question(
        Category category,
        Difficulty difficulty,
        String text,
        List<String> options,
        int answer,
        String fact) {

    /** Options per question. */
    static final int OPTIONS = 4;

    /** Longest question text, so it fits the question card in two or three lines. */
    static final int MAX_TEXT = 160;

    /** Longest option text, so it fits an answer button in at most two lines. */
    static final int MAX_OPTION = 48;

    /** Longest fun fact, so it fits the reveal footer. */
    static final int MAX_FACT = 160;

    Question {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(difficulty, "difficulty");
        text = requireText(text, "the question", MAX_TEXT);
        Objects.requireNonNull(options, "options");
        if (options.size() != OPTIONS) {
            throw new IllegalArgumentException(
                    "needs exactly " + OPTIONS + " answers, found " + options.size());
        }
        List<String> cleaned = new ArrayList<>(OPTIONS);
        Set<String> seen = new HashSet<>();
        for (String option : options) {
            String value = requireText(option, "an answer", MAX_OPTION);
            if (!seen.add(normalize(value))) {
                throw new IllegalArgumentException("the answer \"" + value + "\" appears twice");
            }
            cleaned.add(value);
        }
        options = List.copyOf(cleaned);
        if (answer < 0 || answer >= OPTIONS) {
            throw new IllegalArgumentException("answer index " + answer + " is out of range");
        }
        fact = fact == null || fact.isBlank() ? null : requireText(fact, "the fun fact", MAX_FACT);
    }

    /** The text of the correct option. */
    String correctOption() {
        return options.get(answer);
    }

    /**
     * A copy with the options in random order; {@link #answer()} follows the correct option. Every
     * player sees the same copy, so the order is the same for everyone.
     */
    Question shuffled(RandomGenerator random) {
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order, random);
        List<String> reordered = order.stream().map(options::get).toList();
        return new Question(category, difficulty, text, reordered, order.indexOf(answer), fact);
    }

    /**
     * Normalised form used to detect duplicate questions and answers: trimmed, lower case, single
     * spaces.
     */
    static String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String what, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is blank");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(
                    what + " is longer than " + maxLength + " characters: \"" + stripped + "\"");
        }
        return stripped;
    }
}
