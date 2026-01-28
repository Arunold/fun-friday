package com.example.funfridaygame.model.roundinfo;

import com.example.funfridaygame.model.round.SlidingPuzzleRound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Round info DTO for Sliding Puzzle game. */
@Getter
@Setter
@NoArgsConstructor
public class SlidingPuzzleRoundInfo extends BaseRoundInfo {

    private int gridSize;
    private String difficulty;
    private long startTime;

    // Initial puzzle state (same for all players)
    private int[] initialPuzzle;

    // Player states for the frontend
    private Map<String, PlayerPuzzleInfo> playerStates = new HashMap<>();

    // Ordered list of players who solved
    private List<String> solveOrder = new ArrayList<>();

    public SlidingPuzzleRoundInfo(SlidingPuzzleRound round) {
        super(round);
        this.gridSize = round.getGridSize();
        this.difficulty = round.getDifficulty() != null ? round.getDifficulty().name() : "MEDIUM";
        this.startTime = round.getStartTime();
        this.initialPuzzle = round.getInitialPuzzle();
        this.solveOrder = new ArrayList<>(round.getSolveOrder());

        // Convert player states
        for (Map.Entry<String, SlidingPuzzleRound.PlayerPuzzleState> entry :
                round.getPlayerStates().entrySet()) {
            SlidingPuzzleRound.PlayerPuzzleState state = entry.getValue();
            PlayerPuzzleInfo info = new PlayerPuzzleInfo();
            info.setPuzzle(state.getPuzzle());
            info.setMoves(state.getMoves());
            info.setSolved(state.isSolved());
            info.setSolveTime(state.getSolveTime());
            info.setCorrectTiles(round.countCorrectTiles(state.getPuzzle()));
            this.playerStates.put(entry.getKey(), info);
        }
    }

    /** Player puzzle state for frontend */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlayerPuzzleInfo {
        private int[] puzzle;
        private int moves;
        private boolean solved;
        private long solveTime;
        private int correctTiles;
    }
}
