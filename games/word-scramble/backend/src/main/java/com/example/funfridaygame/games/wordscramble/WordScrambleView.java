package com.example.funfridaygame.games.wordscramble;

import java.util.List;

/**
 * What a browser receives for Word Scramble. The answer is absent until the reveal (BUG-08); hint
 * letters and wrong guesses are only ever sent to the player they belong to.
 *
 * @param scramble the scrambled letters everyone sees
 * @param length number of letters
 * @param revealed whether the round is over
 * @param word the answer (revealed only)
 * @param maxHints hints a player may take this round (0 when hints are off)
 * @param maxMisses wrong guesses allowed per player this round
 * @param me the viewer's own progress ({@code null} for spectators)
 * @param players everyone's progress — never their guesses
 */
public record WordScrambleView(
        String scramble,
        int length,
        boolean revealed,
        String word,
        int maxHints,
        int maxMisses,
        MyStatus me,
        List<PlayerStatus> players) {

    /**
     * The viewer's own progress.
     *
     * @param solved whether they found the word
     * @param rank solve order (solved only)
     * @param timeMillis time from the round start to the solve (solved only)
     * @param misses wrong guesses this round
     * @param recentMisses the latest wrong guesses, most recent first
     * @param hintsUsed hints taken this round
     * @param hints the letters those hints revealed, in place
     */
    public record MyStatus(
            boolean solved,
            Integer rank,
            Long timeMillis,
            int misses,
            List<String> recentMisses,
            int hintsUsed,
            List<HintLetter> hints) {}

    /**
     * A letter revealed by a hint.
     *
     * @param position index in the word, from 0
     * @param letter the letter at that position
     */
    public record HintLetter(int position, String letter) {}

    /**
     * A player's progress as everybody sees it.
     *
     * @param playerId player id
     * @param solved whether they found the word
     * @param rank solve order (solved only)
     * @param timeMillis time from the round start to the solve (solved only)
     * @param hintsUsed hints they took (revealed only)
     * @param misses their number of wrong guesses (revealed only)
     */
    public record PlayerStatus(
            String playerId,
            boolean solved,
            Integer rank,
            Long timeMillis,
            Integer hintsUsed,
            Integer misses) {}
}
