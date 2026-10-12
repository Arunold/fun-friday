package com.example.funfridaygame.games.numberguess;

import java.util.List;

/**
 * What a browser receives for Number Guess. Other players' guesses are hidden until the reveal.
 *
 * @param min lowest possible number
 * @param max highest possible number
 * @param attempts guesses per player per round
 * @param revealed whether the round is over
 * @param target the secret number (only once revealed)
 * @param myGuesses the viewer's guesses with hints ({@code null} for spectators)
 * @param myStatus GUESSING, EXACT or OUT_OF_GUESSES ({@code null} for spectators)
 * @param players progress of every player; guesses and points only once revealed
 */
public record NumberGuessView(
        int min,
        int max,
        int attempts,
        boolean revealed,
        Integer target,
        List<GuessView> myGuesses,
        String myStatus,
        List<PlayerStatus> players) {

    /**
     * One guess.
     *
     * @param value the guessed number
     * @param hint HIGHER (the secret is higher), LOWER or CORRECT
     */
    public record GuessView(int value, String hint) {}

    /**
     * A player's progress.
     *
     * @param playerId player id
     * @param guessesUsed how many guesses they made
     * @param done exact hit or out of guesses
     * @param exact whether they hit the number (revealed only)
     * @param guesses their guesses (revealed only)
     * @param best their closest guess (revealed only)
     */
    public record PlayerStatus(
            String playerId,
            int guessesUsed,
            boolean done,
            Boolean exact,
            List<GuessView> guesses,
            Integer best) {}
}
