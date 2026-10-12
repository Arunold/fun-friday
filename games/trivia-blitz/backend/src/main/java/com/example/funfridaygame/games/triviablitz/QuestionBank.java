package com.example.funfridaygame.games.triviablitz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The curated question bank, validated when it is loaded so a broken line fails fast (at startup
 * and in the tests) instead of in the middle of a game.
 *
 * <p>The bank ships as {@value #RESOURCE} next to this class. One question per line, fields
 * separated by {@code |}:
 *
 * <pre>
 * category | difficulty | question | correct answer | wrong | wrong | wrong | fun fact (optional)
 * </pre>
 *
 * <p>Blank lines and lines starting with {@code #} are ignored. Every question must pass the rules
 * of {@link Question} and appear only once.
 */
final class QuestionBank {

    /** Classpath resource (relative to this class) holding the shipped questions. */
    static final String RESOURCE = "questions.txt";

    private static final int MIN_FIELDS = 7;
    private static final int MAX_FIELDS = 8;

    private final List<Question> questions;

    private QuestionBank(List<Question> questions) {
        this.questions = List.copyOf(questions);
    }

    /** The bank shipped with the game, loaded and validated once. */
    static QuestionBank standard() {
        return Shipped.BANK;
    }

    /**
     * Parses and validates a bank.
     *
     * @param lines the file's lines
     * @param source name used in error messages
     * @throws IllegalStateException listing every invalid line
     */
    static QuestionBank parse(List<String> lines, String source) {
        List<Question> questions = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<String, Integer> firstLine = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            try {
                Question question = parseLine(line);
                Integer previous =
                        firstLine.putIfAbsent(Question.normalize(question.text()), lineNumber);
                if (previous != null) {
                    problems.add(
                            source
                                    + ":"
                                    + lineNumber
                                    + ": duplicate of the question on line "
                                    + previous);
                } else {
                    questions.add(question);
                }
            } catch (IllegalArgumentException e) {
                problems.add(source + ":" + lineNumber + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid trivia question bank:\n" + String.join("\n", problems));
        }
        if (questions.isEmpty()) {
            throw new IllegalStateException(source + " contains no questions");
        }
        return new QuestionBank(questions);
    }

    /** Every question, in file order. */
    List<Question> all() {
        return questions;
    }

    /** The questions a filter allows, in file order. */
    List<Question> matching(QuestionFilter filter) {
        return questions.stream().filter(filter::matches).toList();
    }

    /** How many questions a filter allows. */
    int count(QuestionFilter filter) {
        return (int) questions.stream().filter(filter::matches).count();
    }

    private static Question parseLine(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < MIN_FIELDS || fields.length > MAX_FIELDS) {
            throw new IllegalArgumentException(
                    "expected "
                            + MIN_FIELDS
                            + " or "
                            + MAX_FIELDS
                            + " fields separated by '|', found "
                            + fields.length);
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].strip();
        }
        Category category =
                Category.byId(fields[0])
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "unknown category \"" + fields[0] + "\""));
        Difficulty difficulty =
                Difficulty.byId(fields[1])
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "unknown difficulty \"" + fields[1] + "\""));
        List<String> options = List.of(fields[3], fields[4], fields[5], fields[6]);
        String fact = fields.length == MAX_FIELDS ? fields[7] : null;
        return new Question(category, difficulty, fields[2], options, 0, fact);
    }

    private static List<String> readResource() {
        InputStream stream = QuestionBank.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing trivia question bank " + RESOURCE);
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + RESOURCE, e);
        }
    }

    /** Lazy holder: the shipped bank is read the first time it's needed. */
    private static final class Shipped {
        private static final QuestionBank BANK = parse(readResource(), RESOURCE);

        private Shipped() {}
    }
}
