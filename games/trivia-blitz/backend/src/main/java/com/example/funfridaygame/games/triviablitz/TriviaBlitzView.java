package com.example.funfridaygame.games.triviablitz;

import java.util.List;

/**
 * What a browser receives for Trivia Blitz. Until the reveal nobody learns the correct answer or
 * what anyone else picked — only <em>whether</em> they have answered. The correct option, every
 * pick, answer times and the fun fact are added once the question is revealed.
 *
 * @param number question number in this game, starting at 1
 * @param category category label, e.g. "Science"
 * @param categoryIcon emoji of the category
 * @param difficulty {@code easy}, {@code medium} or {@code hard}
 * @param question the question
 * @param options the four answers, in the same order for everyone
 * @param revealed whether the answer is shown
 * @param myAnswer the viewer's locked-in option (absent for spectators and before answering)
 * @param myStreak the viewer's run of correct answers, updated at the reveal (absent for
 *     spectators)
 * @param answeredCount how many players have answered
 * @param players every player's status, in join order
 * @param correct index of the correct option (revealed only)
 * @param pickers player ids per option, fastest first (revealed only)
 * @param fastest the fastest correct answer (revealed only; absent if nobody was right)
 * @param fact a fun fact about the answer (revealed only, optional)
 */
public record TriviaBlitzView(
        int number,
        String category,
        String categoryIcon,
        String difficulty,
        String question,
        List<String> options,
        boolean revealed,
        Integer myAnswer,
        Integer myStreak,
        int answeredCount,
        List<PlayerStatus> players,
        Integer correct,
        List<List<String>> pickers,
        Fastest fastest,
        String fact) {

    /**
     * A player's status.
     *
     * @param playerId player id
     * @param answered whether they have locked in an answer (never which one before the reveal)
     * @param streak their run of correct answers, updated at the reveal
     * @param correct whether they were right (revealed only)
     * @param answerMillis how long they took to answer (revealed only; absent without an answer)
     * @param lostStreak the streak they just lost, when it was 2 or more (revealed only)
     */
    public record PlayerStatus(
            String playerId,
            boolean answered,
            int streak,
            Boolean correct,
            Long answerMillis,
            Integer lostStreak) {}

    /**
     * The fastest correct answer of a question.
     *
     * @param playerId who answered
     * @param millis how long they took
     */
    public record Fastest(String playerId, long millis) {}
}
