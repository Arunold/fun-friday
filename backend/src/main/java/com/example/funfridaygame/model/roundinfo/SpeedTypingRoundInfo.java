package com.example.funfridaygame.model.roundinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.funfridaygame.model.round.SpeedTypingRound;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Round info DTO for Speed Typing Race game.
 */
@Getter
@Setter
@NoArgsConstructor
public class SpeedTypingRoundInfo extends BaseRoundInfo {
    
    private String targetText;
    private String difficulty;
    private int minAccuracy;
    private long startTime;
    
    // Simplified player progress for frontend
    private Map<String, PlayerProgressInfo> playerProgress = new HashMap<>();
    
    // Ordered list of finishers
    private List<String> rankings = new ArrayList<>();
    
    public SpeedTypingRoundInfo(SpeedTypingRound round, boolean includeTarget) {
        super(round);
        this.targetText = round.getTargetText();
        this.difficulty = round.getDifficulty() != null ? round.getDifficulty().name() : "MEDIUM";
        this.minAccuracy = round.getMinAccuracy();
        this.startTime = round.getStartTime();
        this.rankings = new ArrayList<>(round.getFinishOrder());
        
        // Convert player progress
        for (Map.Entry<String, SpeedTypingRound.PlayerProgress> entry : round.getPlayerProgress().entrySet()) {
            SpeedTypingRound.PlayerProgress progress = entry.getValue();
            PlayerProgressInfo info = new PlayerProgressInfo();
            info.setPercentage(progress.getPercentage());
            info.setAccuracy(progress.getAccuracy());
            info.setFinished(progress.isFinished());
            info.setFinishTime(progress.getFinishTime());
            this.playerProgress.put(entry.getKey(), info);
        }
    }
    
    /**
     * Simplified progress info for frontend
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlayerProgressInfo {
        private int percentage;
        private int accuracy;
        private boolean finished;
        private long finishTime;
    }
}
