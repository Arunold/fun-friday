package com.example.funfridaygame.model.roundinfo;

import java.util.HashMap;
import java.util.Map;

import com.example.funfridaygame.model.round.ReactionShowdownRound;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Round info DTO for Reaction Showdown game.
 * Simple: one reaction opportunity per round.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReactionShowdownRoundInfo extends BaseRoundInfo {

    private String phase;
    private boolean isFakeOut;
    private long signalTime;

    // Player reactions for this round
    private Map<String, ReactionInfo> playerReactions = new HashMap<>();

    // Fastest player and their time
    private String fastestPlayer;
    private long fastestTime;

    public ReactionShowdownRoundInfo(ReactionShowdownRound round) {
        super(round);
        this.phase = round.getPhase().name();
        this.isFakeOut = round.isFakeOut();
        this.signalTime = round.getSignalTime();
        this.fastestPlayer = round.findFastestPlayer();

        // Find fastest time
        long fastest = Long.MAX_VALUE;
        for (Map.Entry<String, ReactionShowdownRound.ReactionData> entry :
                round.getPlayerReactions().entrySet()) {
            ReactionInfo info = new ReactionInfo();
            info.setReactionTime(entry.getValue().getReactionTime());
            info.setFalseStart(entry.getValue().isFalseStart());
            info.setTappedFakeOut(entry.getValue().isTappedFakeOut());
            this.playerReactions.put(entry.getKey(), info);

            if (!entry.getValue().isFalseStart() && !entry.getValue().isTappedFakeOut()
                    && entry.getValue().getReactionTime() > 0
                    && entry.getValue().getReactionTime() < fastest) {
                fastest = entry.getValue().getReactionTime();
            }
        }
        this.fastestTime = fastest == Long.MAX_VALUE ? 0 : fastest;
    }

    /**
     * Simplified reaction info for frontend
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReactionInfo {
        private long reactionTime;
        private boolean falseStart;
        private boolean tappedFakeOut;
    }
}
