package com.example.funfridaygame.games.wordscramble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The curated offline word lists, one per word length, bundled as {@code
 * src/main/resources/word-scramble/words/<length>.txt}. No network is involved (BUG-21): the lists
 * are read once, on first use, through a lazy holder and validated — letters A–Z only, the right
 * length, no duplicates, no two words that are anagrams of each other (every scramble has exactly
 * one answer) and at least {@value #MIN_WORDS} words per length. A broken list fails fast with
 * every problem listed.
 *
 * <p>The resources live in a folder named after the game so they can never clash with another
 * game's files on the shared classpath.
 */
final class WordBank {

    /** Shortest supported word. */
    static final int MIN_LENGTH = 4;

    /** Longest supported word. */
    static final int MAX_LENGTH = 8;

    /** Minimum number of words a list must have. */
    static final int MIN_WORDS = 60;

    private static final String FOLDER = "/word-scramble/words/";

    private WordBank() {}

    /** Initialised by the JVM on first access, exactly once and thread-safely. */
    private static final class Holder {
        private static final Map<Integer, List<String>> WORDS = loadAll();
    }

    /**
     * All words of a length.
     *
     * @throws IllegalArgumentException for unsupported lengths
     */
    static List<String> words(int length) {
        List<String> words = Holder.WORDS.get(length);
        if (words == null) {
            throw new IllegalArgumentException("No words with " + length + " letters");
        }
        return words;
    }

    /**
     * Draws a word that hasn't been used yet in this game (if the list is exhausted, any word).
     *
     * @param length word length
     * @param random the game's random source
     * @param used words already played; not modified
     */
    static String pick(int length, RandomGenerator random, Set<String> used) {
        List<String> all = words(length);
        List<String> fresh = all.stream().filter(word -> !used.contains(word)).toList();
        List<String> pool = fresh.isEmpty() ? all : fresh;
        return pool.get(random.nextInt(pool.size()));
    }

    private static Map<Integer, List<String>> loadAll() {
        Map<Integer, List<String>> all = new LinkedHashMap<>();
        for (int length = MIN_LENGTH; length <= MAX_LENGTH; length++) {
            String path = FOLDER + length + ".txt";
            all.put(length, validate(ResourceLines.read(path), length, path));
        }
        return Map.copyOf(all);
    }

    /**
     * Checks a raw list and returns it as an immutable list.
     *
     * @param lines words as read from the file
     * @param length the length every word must have
     * @param source file name for error messages
     * @throws IllegalStateException listing every problem when the list is invalid
     */
    static List<String> validate(List<String> lines, int length, String source) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> bySignature = new HashMap<>();
        for (String word : lines) {
            if (!word.matches("[A-Z]+")) {
                problems.add(word + " must be upper-case letters A-Z only");
            } else if (word.length() != length) {
                problems.add(word + " doesn't have " + length + " letters");
            } else if (!seen.add(word)) {
                problems.add(word + " is listed twice");
            } else {
                String twin = bySignature.putIfAbsent(signature(word), word);
                if (twin != null) {
                    problems.add(word + " is an anagram of " + twin);
                }
            }
        }
        if (seen.size() < MIN_WORDS) {
            problems.add("needs at least " + MIN_WORDS + " words, has " + seen.size());
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid word list " + source + ": " + problems);
        }
        return List.copyOf(lines);
    }

    /** The word's letters in alphabetical order: equal for anagrams. */
    static String signature(String word) {
        char[] letters = word.toCharArray();
        Arrays.sort(letters);
        return new String(letters);
    }
}
