package com.example.funfridaygame.sdk;

/**
 * Who a {@link GameLogic#view} is rendered for.
 *
 * @param playerId the viewing player, or {@code null} for spectators
 */
public record Viewer(String playerId) {

    /** A spectator (someone watching without playing). */
    public static Viewer spectator() {
        return new Viewer(null);
    }

    /** A player. */
    public static Viewer player(String playerId) {
        return new Viewer(playerId);
    }

    /** Whether the viewer only watches. */
    public boolean isSpectator() {
        return playerId == null;
    }

    /** Whether the viewer is the given player. */
    public boolean is(String id) {
        return id != null && id.equals(playerId);
    }
}
