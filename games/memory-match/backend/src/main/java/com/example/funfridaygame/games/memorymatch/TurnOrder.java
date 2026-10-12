package com.example.funfridaygame.games.memorymatch;

import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Who moves when. Each round snapshots the players in join order, rotated by one seat per round so
 * a different player starts every board. Passing the move skips everyone who is not connected right
 * now (left or disconnected), so an absent player never holds up the board.
 */
final class TurnOrder {

    private final List<String> order = new ArrayList<>();
    private int current = -1;

    /**
     * Sets up the order for the round that is starting and gives the move to the first connected
     * player in it.
     */
    void startRound(GameContext ctx) {
        order.clear();
        List<PlayerInfo> players = ctx.players();
        int shift = players.isEmpty() ? 0 : (ctx.round() - 1) % players.size();
        for (int i = 0; i < players.size(); i++) {
            order.add(players.get((i + shift) % players.size()).id());
        }
        current = -1;
        pass(ctx);
    }

    /**
     * Gives the move to the next connected player after the current one (wrapping around; the
     * current player moves again when nobody else is connected).
     *
     * @return the new mover, or {@code null} when nobody is connected
     */
    String pass(GameContext ctx) {
        for (int step = 1; step <= order.size(); step++) {
            int candidate = Math.floorMod(current + step, order.size());
            if (ctx.isActive(order.get(candidate))) {
                current = candidate;
                return order.get(candidate);
            }
        }
        return null;
    }

    /** The player whose move it is, or {@code null} before the first connected player was found. */
    String mover() {
        return current < 0 ? null : order.get(current);
    }

    /** The round's order of play (player ids, including players who have left since). */
    List<String> order() {
        return List.copyOf(order);
    }
}
