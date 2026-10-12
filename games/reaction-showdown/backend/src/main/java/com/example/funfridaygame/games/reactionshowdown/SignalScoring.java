package com.example.funfridaygame.games.reactionshowdown;

import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.Entry;
import com.example.funfridaygame.games.reactionshowdown.ReactionShowdownView.SignalResult;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Scores one signal: valid taps are ranked by reaction time (ties share a rank and its points),
 * false starts cost {@value #FALSE_START_PENALTY}, no tap scores 0.
 *
 * <p>Points per rank: 100, 80, 65, 50, 40, 30, then 5 less per rank down to a minimum of 10.
 */
final class SignalScoring {

    static final int FALSE_START_PENALTY = -25;
    private static final int[] RANK_POINTS = {100, 80, 65, 50, 40, 30};
    private static final int RANK_STEP = 5;
    private static final int MIN_POINTS = 10;

    private SignalScoring() {}

    /** Points for a 1-based rank among valid taps. */
    static int pointsForRank(int rank) {
        if (rank <= RANK_POINTS.length) {
            return RANK_POINTS[rank - 1];
        }
        int last = RANK_POINTS[RANK_POINTS.length - 1];
        return Math.max(MIN_POINTS, last - RANK_STEP * (rank - RANK_POINTS.length));
    }

    /**
     * Scores a signal for the players still in the game (reactions of departed players are
     * dropped).
     *
     * @param signal the signal with its reactions
     * @param players the players still in the game, in join order
     */
    static SignalResult score(Signal signal, List<PlayerInfo> players) {
        List<Map.Entry<String, Reaction>> valid =
                signal.reactions().entrySet().stream()
                        .filter(tap -> tap.getValue().isValid())
                        .filter(tap -> players.stream().anyMatch(p -> p.id().equals(tap.getKey())))
                        .sorted(Comparator.comparingInt(tap -> tap.getValue().ms()))
                        .toList();
        List<Entry> entries = new ArrayList<>();
        int rank = 0;
        int previousMs = -1;
        for (int i = 0; i < valid.size(); i++) {
            int ms = valid.get(i).getValue().ms();
            if (ms != previousMs) {
                rank = i + 1;
                previousMs = ms;
            }
            entries.add(
                    new Entry(valid.get(i).getKey(), ms, rank, false, false, pointsForRank(rank)));
        }
        List<Entry> fouls = new ArrayList<>();
        for (PlayerInfo player : players) {
            Reaction reaction = signal.reaction(player.id());
            if (reaction == null) {
                entries.add(new Entry(player.id(), null, null, false, false, 0));
            } else if (!reaction.isValid()) {
                fouls.add(
                        new Entry(
                                player.id(),
                                null,
                                null,
                                true,
                                reaction.onFake(),
                                FALSE_START_PENALTY));
            }
        }
        entries.addAll(fouls);
        Entry fastest = valid.isEmpty() ? null : entries.getFirst();
        return new SignalResult(
                signal.id(),
                signal.index() + 1,
                signal.fakeShown(),
                fastest == null ? null : fastest.playerId(),
                fastest == null ? null : fastest.ms(),
                List.copyOf(entries));
    }
}
