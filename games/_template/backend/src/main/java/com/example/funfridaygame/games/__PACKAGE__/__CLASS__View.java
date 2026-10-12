package com.example.funfridaygame.games.__PACKAGE__;

import java.util.List;

/**
 * What a browser receives for __GAME_NAME__. Other players' picks stay hidden until the reveal.
 *
 * @param highest picks go from 1 to this number
 * @param revealed whether the turn is over
 * @param lucky the lucky number (revealed only)
 * @param myPick the viewer's pick ({@code null} for spectators or before picking)
 * @param players everyone's status
 */
public record __CLASS__View(
        int highest, boolean revealed, Integer lucky, Integer myPick, List<PlayerPick> players) {

    /**
     * A player's status.
     *
     * @param playerId player id
     * @param picked whether they picked already
     * @param value their pick (revealed only)
     */
    public record PlayerPick(String playerId, boolean picked, Integer value) {}
}
