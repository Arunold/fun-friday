package com.example.funfridaygame.games.speedtyping;

import java.util.List;

/**
 * What a browser receives for Speed Typing. Everybody sees the text and everyone's progress; what a
 * player typed is only ever sent back to that player (to restore the input after a refresh).
 * Others' speed shows once they finish, the full table at the reveal.
 *
 * @param text the text everybody types this round
 * @param difficulty SHORT, MEDIUM or LONG
 * @param minAccuracy minimum accuracy in percent; finishers below it get half points
 * @param endsAt server time when the round ends (the browser stops syncing just before it)
 * @param revealed whether the round is over
 * @param me the viewer's own race ({@code null} for spectators)
 * @param lanes everyone's progress, in join order
 * @param results the final table, best first (only once revealed)
 */
public record SpeedTypingView(
        String text,
        String difficulty,
        int minAccuracy,
        long endsAt,
        boolean revealed,
        MyRace me,
        List<Lane> lanes,
        List<ResultRow> results) {

    /**
     * The viewer's own race.
     *
     * @param typed everything they typed so far
     * @param keystrokes characters typed in total, including corrected ones
     * @param errors characters that were wrong when typed
     * @param startedAt server time of their first keystroke ({@code null} before it)
     * @param progress correct prefix in percent (100 once finished)
     * @param finished whether they typed the text exactly
     * @param rank finishing place (finished only)
     * @param wpm words per minute (finished or revealed only)
     * @param accuracy accuracy in whole percent (finished or revealed only)
     * @param timeMs time from the start of the round to the finish (finished only)
     * @param penalized finished below the minimum accuracy, so the points are halved
     */
    public record MyRace(
            String typed,
            int keystrokes,
            int errors,
            Long startedAt,
            int progress,
            boolean finished,
            Integer rank,
            Integer wpm,
            Integer accuracy,
            Long timeMs,
            boolean penalized) {}

    /**
     * A player's lane on the race track.
     *
     * @param playerId player id
     * @param progress correct prefix in percent (100 once finished)
     * @param finished whether they typed the text exactly
     * @param rank finishing place (finished only)
     * @param wpm words per minute (finished only)
     */
    public record Lane(
            String playerId, int progress, boolean finished, Integer rank, Integer wpm) {}

    /**
     * A row of the final table.
     *
     * @param playerId player id
     * @param finished whether they typed the text exactly
     * @param rank finishing place (finished only)
     * @param progress correct prefix in percent
     * @param wpm words per minute ({@code null} if they never typed)
     * @param accuracy accuracy in whole percent ({@code null} if they never typed)
     * @param timeMs time from the start of the round to the finish (finished only)
     * @param penalized finished below the minimum accuracy, so the points were halved
     */
    public record ResultRow(
            String playerId,
            boolean finished,
            Integer rank,
            int progress,
            Integer wpm,
            Integer accuracy,
            Long timeMs,
            boolean penalized) {}
}
