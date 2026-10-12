package com.example.funfridaygame.games.wordscramble;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One player's progress in the current round: misses, hints and — once solved — rank and time. */
final class PlayerRound {

    /** How many recent misses the player sees. */
    static final int RECENT_MISSES = 5;

    private final Set<String> tried = new HashSet<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private int misses;
    private int hintsUsed;
    private int rank;
    private long solvedAt;

    /** Whether the player found the word. */
    boolean solved() {
        return rank > 0;
    }

    /** Solve order (1 = first), 0 while unsolved. */
    int rank() {
        return rank;
    }

    /** When the player solved (epoch ms); only meaningful once {@link #solved()}. */
    long solvedAt() {
        return solvedAt;
    }

    /** Wrong guesses this round. */
    int misses() {
        return misses;
    }

    /** Hints taken this round. */
    int hintsUsed() {
        return hintsUsed;
    }

    /** Whether this exact wrong guess was already made this round. */
    boolean alreadyTried(String guess) {
        return tried.contains(guess);
    }

    /** The latest wrong guesses, most recent first. */
    List<String> recentMisses() {
        return List.copyOf(recent);
    }

    /** Records a wrong guess. */
    void miss(String guess) {
        misses++;
        tried.add(guess);
        recent.addFirst(guess);
        if (recent.size() > RECENT_MISSES) {
            recent.removeLast();
        }
    }

    /** Records one more hint. */
    void takeHint() {
        hintsUsed++;
    }

    /** Locks the player in as solved. */
    void solve(int solveRank, long at) {
        this.rank = solveRank;
        this.solvedAt = at;
    }
}
