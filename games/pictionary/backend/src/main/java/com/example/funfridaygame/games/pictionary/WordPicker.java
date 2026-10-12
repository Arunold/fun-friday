package com.example.funfridaygame.games.pictionary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Offers every drawer three words and makes sure no word comes back within a game: words that were
 * offered are only offered again when a level runs out, and drawn words never repeat.
 */
final class WordPicker {

    /** The "difficulty" setting: one level for every word, or one word of each level. */
    enum Mode {
        EASY,
        MEDIUM,
        HARD,
        MIXED;

        /** Parses the setting value ({@code easy}, {@code medium}, {@code hard}, {@code mixed}). */
        static Mode fromSetting(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }

        /** The setting value. */
        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        private List<WordList.Level> levels() {
            return switch (this) {
                case EASY -> List.of(WordList.Level.EASY, WordList.Level.EASY, WordList.Level.EASY);
                case MEDIUM ->
                        List.of(
                                WordList.Level.MEDIUM,
                                WordList.Level.MEDIUM,
                                WordList.Level.MEDIUM);
                case HARD -> List.of(WordList.Level.HARD, WordList.Level.HARD, WordList.Level.HARD);
                case MIXED ->
                        List.of(WordList.Level.EASY, WordList.Level.MEDIUM, WordList.Level.HARD);
            };
        }
    }

    /**
     * A word offered to the drawer.
     *
     * @param word the word
     * @param level how hard it is
     */
    record Choice(String word, WordList.Level level) {}

    private final WordList list;
    private final Mode mode;
    private final Set<String> offered = new HashSet<>();
    private final Set<String> chosen = new HashSet<>();

    /** A picker for one game. */
    WordPicker(WordList list, Mode mode) {
        this.list = list;
        this.mode = mode;
    }

    /** Three different words for the next drawer. */
    List<Choice> next(RandomGenerator random) {
        Set<String> taken = new HashSet<>();
        List<Choice> choices = new ArrayList<>();
        for (WordList.Level level : mode.levels()) {
            String word = pick(level, random, taken);
            taken.add(word);
            choices.add(new Choice(word, level));
        }
        offered.addAll(taken);
        return List.copyOf(choices);
    }

    /** Remembers the word that was drawn so it never comes back. */
    void markChosen(String word) {
        chosen.add(word);
    }

    private String pick(WordList.Level level, RandomGenerator random, Set<String> taken) {
        List<String> all = list.words(level);
        List<String> candidates =
                all.stream().filter(w -> !taken.contains(w) && !offered.contains(w)).toList();
        if (candidates.isEmpty()) {
            candidates =
                    all.stream().filter(w -> !taken.contains(w) && !chosen.contains(w)).toList();
        }
        if (candidates.isEmpty()) {
            candidates = all.stream().filter(w -> !taken.contains(w)).toList();
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Not enough " + level + " words");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
