package com.example.funfridaygame.games.speedtyping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The curated texts, one list per {@link TextLength}, loaded once from {@code texts/*.txt} next to
 * this class (one text per line; {@code #} comments and blank lines are ignored).
 *
 * <p>Every text must be plain printable ASCII (straight quotes, typeable on any keyboard), without
 * leading, trailing or double spaces, and within the character range of its length. A broken file
 * fails fast when the library loads; {@code TextLibraryTest} checks the shipped files.
 */
final class TextLibrary {

    private final Map<TextLength, List<String>> texts;

    /** A library with the given texts per length; every text is validated. */
    TextLibrary(Map<TextLength, List<String>> texts) {
        Map<TextLength, List<String>> copy = new EnumMap<>(TextLength.class);
        for (TextLength length : TextLength.values()) {
            List<String> list = List.copyOf(texts.getOrDefault(length, List.of()));
            if (list.isEmpty()) {
                throw new IllegalArgumentException("No " + length + " texts");
            }
            for (String text : list) {
                List<String> problems = problems(text, length);
                if (!problems.isEmpty()) {
                    throw new IllegalArgumentException(
                            length + " text \"" + text + "\": " + String.join(", ", problems));
                }
            }
            if (new HashSet<>(list).size() != list.size()) {
                throw new IllegalArgumentException("Duplicate " + length + " texts");
            }
            copy.put(length, list);
        }
        this.texts = copy;
    }

    /** The texts shipped with the game (loaded on first use). */
    static TextLibrary standard() {
        return Standard.INSTANCE;
    }

    /** All texts of one length, in file order. */
    List<String> texts(TextLength length) {
        return texts.get(length);
    }

    /**
     * Picks a random text of the given length that hasn't been used yet in this game. When every
     * text was used already (longer games than the list), any text may come again.
     */
    String pick(TextLength length, Set<String> used, RandomGenerator random) {
        List<String> all = texts(length);
        List<String> fresh = all.stream().filter(text -> !used.contains(text)).toList();
        List<String> candidates = fresh.isEmpty() ? all : fresh;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** Why a text is not acceptable for a length; empty when it is fine. */
    static List<String> problems(String text, TextLength length) {
        List<String> problems = new ArrayList<>();
        if (text.length() < length.minChars() || text.length() > length.maxChars()) {
            problems.add(
                    text.length()
                            + " characters, expected "
                            + length.minChars()
                            + " to "
                            + length.maxChars());
        }
        if (!text.chars().allMatch(c -> c >= ' ' && c <= '~')) {
            problems.add("only printable ASCII characters are allowed");
        }
        if (!text.equals(text.strip()) || text.contains("  ")) {
            problems.add("no leading, trailing or double spaces");
        }
        return problems;
    }

    private static TextLibrary load() {
        Map<TextLength, List<String>> texts = new EnumMap<>(TextLength.class);
        for (TextLength length : TextLength.values()) {
            texts.put(length, read(length.resource()));
        }
        return new TextLibrary(texts);
    }

    private static List<String> read(String resource) {
        InputStream in = TextLibrary.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + resource);
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + resource, e);
        }
    }

    /** Lazy holder: the files are read once, on first use. */
    private static final class Standard {
        private static final TextLibrary INSTANCE = load();
    }
}
