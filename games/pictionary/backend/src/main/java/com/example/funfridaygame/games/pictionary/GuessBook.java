package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything guessed during one turn: the feed everybody sees, per-player limits and the correct
 * guesses in order (BUG-09: every guess shows up live, every correct guesser is recognised).
 */
final class GuessBook {

    /** Longest guess shown in the feed. */
    static final int MAX_TEXT = 40;

    /** How a guess was judged; {@link #CLOSE} lines are only shown to their author. */
    enum Kind {
        GUESS,
        CLOSE,
        CORRECT
    }

    /**
     * One line of the feed.
     *
     * @param id increasing number within the turn
     * @param playerId who guessed
     * @param name their name at the time
     * @param kind how the guess was judged
     * @param text the guess ({@code null} for correct guesses)
     */
    record Entry(int id, String playerId, String name, Kind kind, String text) {}

    /**
     * A correct guess.
     *
     * @param playerId who guessed
     * @param name their name at the time
     * @param elapsedMillis time since the drawing phase started
     * @param points points earned
     */
    record Correct(String playerId, String name, long elapsedMillis, int points) {}

    private final List<Entry> feed = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Set<String>> tried = new HashMap<>();
    private final List<Correct> correct = new ArrayList<>();

    /**
     * Checks that the player may make this guess and counts it. Repeated guesses don't count.
     *
     * @return the guess as shown in the feed: trimmed, single spaces, at most {@value #MAX_TEXT}
     *     characters
     * @throws InvalidActionException when the player already guessed the word, used up their
     *     guesses, sent nothing guessable or repeats a guess
     */
    String register(String playerId, String raw, int maxGuesses) {
        if (hasGuessed(playerId)) {
            throw new InvalidActionException("You already guessed it!");
        }
        String text = clean(raw);
        String key = WordMatcher.compact(WordMatcher.normalize(text));
        if (key.isEmpty()) {
            throw new InvalidActionException("Type a word to guess");
        }
        int used = counts.getOrDefault(playerId, 0);
        if (used >= maxGuesses) {
            throw new InvalidActionException(
                    "That's " + maxGuesses + " guesses — wait for the next drawing");
        }
        if (!tried.computeIfAbsent(playerId, id -> new HashSet<>()).add(key)) {
            throw new InvalidActionException("You already tried “" + text + "”");
        }
        counts.put(playerId, used + 1);
        return text;
    }

    /** Adds a wrong guess, visible to everybody. */
    void addWrong(PlayerInfo player, String text) {
        feed.add(new Entry(feed.size() + 1, player.id(), player.name(), Kind.GUESS, text));
    }

    /** Adds a near miss, visible only to its author. */
    void addClose(PlayerInfo player, String text) {
        feed.add(new Entry(feed.size() + 1, player.id(), player.name(), Kind.CLOSE, text));
    }

    /** Adds a correct guess; the feed only says that the player got it, never the word. */
    void addCorrect(PlayerInfo player, long elapsedMillis, int points) {
        correct.add(new Correct(player.id(), player.name(), elapsedMillis, points));
        feed.add(new Entry(feed.size() + 1, player.id(), player.name(), Kind.CORRECT, null));
    }

    /** Whether the player guessed the word. */
    boolean hasGuessed(String playerId) {
        return correct.stream().anyMatch(c -> c.playerId().equals(playerId));
    }

    /** Whether nobody guessed the word yet. */
    boolean noneCorrect() {
        return correct.isEmpty();
    }

    /** Guesses the player may still make. */
    int guessesLeft(String playerId, int maxGuesses) {
        return Math.max(0, maxGuesses - counts.getOrDefault(playerId, 0));
    }

    /** Correct guesses in order. */
    List<Correct> correct() {
        return List.copyOf(correct);
    }

    /**
     * The newest feed lines a viewer may see: public lines plus their own near misses.
     *
     * @param viewerId the viewing player, {@code null} for spectators
     * @param limit how many lines at most
     */
    List<Entry> feedFor(String viewerId, int limit) {
        List<Entry> lines =
                feed.stream()
                        .filter(e -> e.kind() != Kind.CLOSE || e.playerId().equals(viewerId))
                        .toList();
        return lines.subList(Math.max(0, lines.size() - limit), lines.size());
    }

    /** Trims, collapses whitespace and shortens a guess for the feed. */
    static String clean(String raw) {
        String text = raw.strip().replaceAll("\\s+", " ");
        if (text.length() <= MAX_TEXT) {
            return text;
        }
        int end = MAX_TEXT;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end).strip();
    }
}
