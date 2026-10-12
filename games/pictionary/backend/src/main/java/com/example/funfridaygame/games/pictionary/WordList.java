package com.example.funfridaygame.games.pictionary;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The curated, family-friendly word lists ({@code words-easy.txt}, {@code words-medium.txt}, {@code
 * words-hard.txt} next to this class), loaded once and shared by all games.
 */
final class WordList {

    /** How hard a word is to draw. */
    enum Level {
        EASY,
        MEDIUM,
        HARD;

        private String resource() {
            return "words-" + name().toLowerCase(Locale.ROOT) + ".txt";
        }
    }

    /** Lower-case letters; words of a phrase are separated by one space or hyphen. */
    private static final Pattern VALID = Pattern.compile("[a-z]+([ -][a-z]+)*");

    private final Map<Level, List<String>> words;

    /** A word list from explicit words per level (used by tests). */
    WordList(Map<Level, List<String>> words) {
        Map<Level, List<String>> copy = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            copy.put(level, List.copyOf(words.getOrDefault(level, List.of())));
        }
        this.words = copy;
    }

    /** The bundled lists. */
    static WordList standard() {
        return Holder.STANDARD;
    }

    /** All words of a level. */
    List<String> words(Level level) {
        return words.get(level);
    }

    /** Lazily loads the bundled lists on first use. */
    private static final class Holder {
        private static final WordList STANDARD = load();
    }

    private static WordList load() {
        Map<Level, List<String>> lists = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            lists.put(level, read(level.resource()));
        }
        return new WordList(lists);
    }

    private static List<String> read(String resource) {
        try (InputStream in = WordList.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing word list " + resource);
            }
            Set<String> words = new LinkedHashSet<>();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String word = line.strip().toLowerCase(Locale.ROOT);
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                if (!VALID.matcher(word).matches()) {
                    throw new IllegalStateException("Invalid word '" + word + "' in " + resource);
                }
                words.add(word);
            }
            return new ArrayList<>(words);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + resource, e);
        }
    }
}
