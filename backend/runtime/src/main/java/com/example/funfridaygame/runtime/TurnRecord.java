package com.example.funfridaygame.runtime;

import java.util.Map;

/**
 * Score breakdown of one finished turn.
 *
 * @param round round number
 * @param turn turn number within the round
 * @param points points earned per player id
 * @param headline the reveal headline
 */
public record TurnRecord(int round, int turn, Map<String, Integer> points, String headline) {

    public TurnRecord {
        points = Map.copyOf(points);
    }
}
