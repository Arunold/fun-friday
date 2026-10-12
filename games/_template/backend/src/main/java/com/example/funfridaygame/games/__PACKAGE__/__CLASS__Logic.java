package com.example.funfridaygame.games.__PACKAGE__;

import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rules of __GAME_NAME__. One turn per round; picks stay secret until the reveal. */
final class __CLASS__Logic implements GameLogic {

    static final String PICK = "pick";

    private final int highest;
    private final Map<String, Integer> picks = new LinkedHashMap<>();
    private int lucky;
    private boolean revealed;

    /** Payload of the {@value #PICK} action. */
    record Pick(@NotNull Integer value) {}

    __CLASS__Logic(int highest) {
        this.highest = highest;
    }

    @Override
    public void startTurn(GameContext ctx) {
        picks.clear();
        revealed = false;
        lucky = 1 + ctx.random().nextInt(highest);
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!PICK.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        int value = action.payload(Pick.class).value();
        if (value < 1 || value > highest) {
            throw new InvalidActionException("Pick a number from 1 to " + highest);
        }
        if (picks.containsKey(player.id())) {
            throw new InvalidActionException("You already picked");
        }
        picks.put(player.id(), value);
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        return ctx.activePlayers().stream().allMatch(p -> picks.containsKey(p.id()));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        TurnOutcome.Builder outcome =
                TurnOutcome.builder().headline("The lucky number was " + lucky);
        int best = picks.values().stream().mapToInt(v -> Math.abs(v - lucky)).min().orElse(-1);
        picks.forEach(
                (playerId, value) -> {
                    int distance = Math.abs(value - lucky);
                    if (distance == 0) {
                        outcome.add(playerId, 20);
                    } else if (distance == best) {
                        outcome.add(playerId, 10);
                    }
                });
        return outcome.build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        List<__CLASS__View.PlayerPick> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            players.add(
                    new __CLASS__View.PlayerPick(
                            player.id(),
                            picks.containsKey(player.id()),
                            revealed ? picks.get(player.id()) : null));
        }
        return new __CLASS__View(
                highest,
                revealed,
                revealed ? lucky : null,
                viewer.isSpectator() ? null : picks.get(viewer.playerId()),
                players);
    }

    @Override
    public Object debugState(GameContext ctx) {
        return Map.of("lucky", lucky);
    }
}
