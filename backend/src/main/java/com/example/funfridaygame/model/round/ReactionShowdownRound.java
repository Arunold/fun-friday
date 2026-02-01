package com.example.funfridaygame.model.round;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Round implementation for Reaction Showdown game. Players test their reaction time by tapping when
 * the signal appears. Simple: one reaction opportunity per round, like other games.
 */
@Getter
@Setter
public class ReactionShowdownRound extends BaseGameRound {

    public enum Phase {
        WAITING, // Players are waiting for the signal
        REACT, // GO signal - players should tap now!
        FAKEOUT // Fake-out - players should NOT tap
    }

    // Current phase
    private Phase phase = Phase.WAITING;

    // When the GO signal was shown (for calculating reaction times)
    private long signalTime = 0;

    // Whether this round is a fake-out
    private boolean isFakeOut = false;

    // Configuration
    private int minDelay;
    private int maxDelay;
    private boolean includeFakeOuts;
    private int fakeOutChance;

    // Player reactions: playerName -> ReactionData
    private final Map<String, ReactionData> playerReactions = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public ReactionShowdownRound() {
        super();
    }

    public ReactionShowdownRound(
            int roundNumber,
            int minDelay,
            int maxDelay,
            boolean includeFakeOuts,
            int fakeOutChance) {
        super(roundNumber);
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.includeFakeOuts = includeFakeOuts;
        this.fakeOutChance = fakeOutChance;

        // Determine if this round is a fake-out
        this.isFakeOut = includeFakeOuts && random.nextInt(100) < fakeOutChance;
    }

    /** Get the random delay before signal appears */
    public int getSignalDelay() {
        return minDelay + random.nextInt(maxDelay - minDelay);
    }

    /** Signal that the GO (or fake-out) signal has been shown */
    public void triggerSignal() {
        signalTime = System.currentTimeMillis();
        phase = isFakeOut ? Phase.FAKEOUT : Phase.REACT;
    }

    /** Record a player's tap */
    public void recordTap(String playerName, long tapTime) {
        if (playerReactions.containsKey(playerName)) {
            return; // Already tapped
        }

        ReactionData data = new ReactionData();
        data.setTapTime(tapTime);

        switch (phase) {
            case WAITING -> {
                // False start - tapped before signal
                data.setFalseStart(true);
                data.setReactionTime(-1);
            }
            case FAKEOUT -> {
                // Tapped on fake-out
                data.setTappedFakeOut(true);
                data.setReactionTime(-1);
            }
            case REACT -> {
                // Valid tap - calculate reaction time
                data.setReactionTime(tapTime - signalTime);
            }
            default -> throw new IllegalStateException("Unexpected phase: " + phase);
        }

        playerReactions.put(playerName, data);
    }

    /** Calculate points for a player based on their reaction */
    public int calculatePlayerPoints(String playerName, String fastestPlayer) {
        ReactionData data = playerReactions.get(playerName);
        if (data == null) {
            // Didn't tap - no points but no penalty
            return 0;
        }

        if (data.isFalseStart()) {
            return -50; // Penalty for false start
        }

        if (data.isTappedFakeOut()) {
            return -25; // Penalty for tapping on fake-out
        }

        // Valid reaction
        int points = 100; // Base points

        // Speed bonus: +1 per 10ms faster than 500ms
        if (data.getReactionTime() < 500) {
            points += (int) ((500 - data.getReactionTime()) / 10);
        }

        // Fastest player bonus
        if (playerName.equals(fastestPlayer)) {
            points += 50;
        }

        return points;
    }

    /** Find the fastest valid reaction */
    public String findFastestPlayer() {
        String fastest = null;
        long fastestTime = Long.MAX_VALUE;

        for (Map.Entry<String, ReactionData> entry : playerReactions.entrySet()) {
            ReactionData data = entry.getValue();
            if (!data.isFalseStart() && !data.isTappedFakeOut() && data.getReactionTime() > 0) {
                if (data.getReactionTime() < fastestTime) {
                    fastestTime = data.getReactionTime();
                    fastest = entry.getKey();
                }
            }
        }

        return fastest;
    }

    @Override
    public boolean hasPlayerAnswered(String playerName) {
        return playerReactions.containsKey(playerName);
    }

    @Override
    public int getAnsweredCount() {
        return playerReactions.size();
    }

    @Override
    public String calculateWinner() {
        this.winner = findFastestPlayer();
        return this.winner;
    }

    /** Inner class to track player reaction data */
    @Getter
    @Setter
    public static class ReactionData {
        private long tapTime;
        private long reactionTime = -1;
        private boolean falseStart = false;
        private boolean tappedFakeOut = false;
    }
}
