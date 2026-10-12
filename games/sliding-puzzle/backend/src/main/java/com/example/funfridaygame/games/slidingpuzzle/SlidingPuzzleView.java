package com.example.funfridaygame.games.slidingpuzzle;

import java.util.List;

/**
 * What a browser receives for Sliding Puzzle. Every viewer gets the same starting scramble, their
 * own board and everyone's progress — never another player's board.
 *
 * @param round the round number; a new number means a new puzzle
 * @param size cells per row (3, 4 or 5)
 * @param revealed whether the round is over
 * @param start the round's starting scramble in reading order, {@code 0} = gap (the same for all)
 * @param board the viewer's board ({@code null} for spectators)
 * @param moves tiles the viewer moved ({@code null} for spectators)
 * @param finished whether the viewer solved the puzzle ({@code null} for spectators)
 * @param rank the viewer's finishing position (solvers only)
 * @param timeMs the viewer's solve time in milliseconds (solvers only)
 * @param players progress of every player, in join order
 */
public record SlidingPuzzleView(
        int round,
        int size,
        boolean revealed,
        List<Integer> start,
        List<Integer> board,
        Integer moves,
        Boolean finished,
        Integer rank,
        Long timeMs,
        List<PlayerStatus> players) {

    /**
     * A player's progress, without their board.
     *
     * @param playerId player id
     * @param inPlace tiles at their home cell
     * @param progress {@code inPlace} as a percentage of all tiles
     * @param moves tiles moved
     * @param finished whether they solved the puzzle
     * @param rank finishing position (solvers only)
     * @param timeMs solve time in milliseconds (solvers only)
     */
    public record PlayerStatus(
            String playerId,
            int inPlace,
            int progress,
            int moves,
            boolean finished,
            Integer rank,
            Long timeMs) {}
}
