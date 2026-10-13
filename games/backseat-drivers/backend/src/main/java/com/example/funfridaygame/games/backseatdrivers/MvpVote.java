package com.example.funfridaygame.games.backseatdrivers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MVP vote after the last level: every player votes for someone else; the most-voted players
 * are the team's MVPs. A vote can be changed until the vote ends.
 */
final class MvpVote {

    private final Map<String, String> votes = new LinkedHashMap<>();
    private boolean revealed;

    /** Records (or replaces) a player's vote. */
    void cast(String voterId, String candidateId) {
        votes.put(voterId, candidateId);
    }

    /** Whether the player has voted. */
    boolean hasVoted(String playerId) {
        return votes.containsKey(playerId);
    }

    /** Whom the player voted for, or {@code null}. */
    String voteOf(String playerId) {
        return votes.get(playerId);
    }

    /** Who has voted, in voting order. */
    List<String> voters() {
        return List.copyOf(votes.keySet());
    }

    /** Ends the vote. */
    void reveal() {
        revealed = true;
    }

    boolean revealed() {
        return revealed;
    }

    /**
     * Votes per candidate, counting only voters and candidates that are still in the game.
     *
     * @param inGame player ids still in the game, in join order (the result follows this order)
     */
    Map<String, Integer> tally(List<String> inGame) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (String candidate : inGame) {
            int count = 0;
            for (Map.Entry<String, String> vote : votes.entrySet()) {
                if (candidate.equals(vote.getValue()) && inGame.contains(vote.getKey())) {
                    count++;
                }
            }
            if (count > 0) {
                tally.put(candidate, count);
            }
        }
        return tally;
    }

    /** The most-voted players (ties share the title); empty without votes. */
    List<String> winners(List<String> inGame) {
        Map<String, Integer> tally = tally(inGame);
        int best = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> winners = new ArrayList<>();
        tally.forEach(
                (candidate, count) -> {
                    if (count == best) {
                        winners.add(candidate);
                    }
                });
        return winners;
    }
}
