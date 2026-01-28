package com.example.funfridaygame.model.round;

import com.example.funfridaygame.model.config.SlidingPuzzleConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Round implementation for Sliding Puzzle game. Players race to solve the same shuffled puzzle. The
 * puzzle is a classic sliding puzzle (like 15-puzzle) where tiles are numbered and one space is
 * empty.
 */
@Getter
@Setter
public class SlidingPuzzleRound extends BaseGameRound {

    private int gridSize;
    private SlidingPuzzleConfig.Difficulty difficulty;
    private long startTime;

    // The initial shuffled puzzle state (same for all players)
    private int[] initialPuzzle;

    // Player progress: playerName -> PlayerPuzzleState
    private final Map<String, PlayerPuzzleState> playerStates = new ConcurrentHashMap<>();

    // Ordered list of players who solved the puzzle
    private final List<String> solveOrder = Collections.synchronizedList(new ArrayList<>());

    public SlidingPuzzleRound() {
        super();
    }

    public SlidingPuzzleRound(int roundNumber, int gridSize, SlidingPuzzleConfig.Difficulty diff) {
        super(roundNumber);
        this.gridSize = gridSize;
        this.difficulty = diff;
        this.initialPuzzle = generateSolvablePuzzle(gridSize);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Generates a solvable sliding puzzle. Uses Fisher-Yates shuffle and ensures the puzzle is
     * solvable by checking inversion count.
     */
    private int[] generateSolvablePuzzle(int size) {
        int totalTiles = size * size;
        int[] puzzle;
        Random random = new Random();

        do {
            puzzle = new int[totalTiles];
            // Fill with 1 to (size*size - 1), 0 represents empty space
            for (int i = 0; i < totalTiles - 1; i++) {
                puzzle[i] = i + 1;
            }
            puzzle[totalTiles - 1] = 0; // Empty space at the end

            // Fisher-Yates shuffle
            for (int i = totalTiles - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = puzzle[i];
                puzzle[i] = puzzle[j];
                puzzle[j] = temp;
            }
        } while (!isSolvable(puzzle, size));

        return puzzle;
    }

    /**
     * Checks if the puzzle is solvable using inversion count. A puzzle is solvable if: - For
     * odd-sized grids: number of inversions is even - For even-sized grids: (inversions + row of
     * blank from bottom) is odd
     */
    private boolean isSolvable(int[] puzzle, int size) {
        int inversions = 0;
        int blankRow = 0;

        for (int i = 0; i < puzzle.length; i++) {
            if (puzzle[i] == 0) {
                blankRow = i / size;
                continue;
            }
            for (int j = i + 1; j < puzzle.length; j++) {
                if (puzzle[j] != 0 && puzzle[i] > puzzle[j]) {
                    inversions++;
                }
            }
        }

        if (size % 2 == 1) {
            // Odd grid: solvable if inversions is even
            return inversions % 2 == 0;
        } else {
            // Even grid: solvable if (inversions + blank row from bottom) is odd
            int blankRowFromBottom = size - blankRow;
            return (inversions + blankRowFromBottom) % 2 == 1;
        }
    }

    /** Initialize a player's puzzle state with the same initial puzzle. */
    public void initializePlayer(String playerName) {
        if (!playerStates.containsKey(playerName)) {
            PlayerPuzzleState state = new PlayerPuzzleState(initialPuzzle.clone());
            playerStates.put(playerName, state);
        }
    }

    /**
     * Process a move for a player. Returns true if the puzzle was solved with this move.
     *
     * @param playerName The player making the move
     * @param tileIndex The index of the tile to move
     * @return true if this move solved the puzzle
     */
    public boolean makeMove(String playerName, int tileIndex) {
        PlayerPuzzleState state = playerStates.get(playerName);
        if (state == null || state.isSolved()) {
            return false;
        }

        int[] puzzle = state.getPuzzle();
        int emptyIndex = findEmptyIndex(puzzle);

        // Check if the move is valid (tile is adjacent to empty space)
        if (!isValidMove(tileIndex, emptyIndex)) {
            return false;
        }

        // Swap tile with empty space
        puzzle[emptyIndex] = puzzle[tileIndex];
        puzzle[tileIndex] = 0;
        state.incrementMoves();

        // Check if puzzle is solved
        if (isPuzzleSolved(puzzle)) {
            state.setSolved(true);
            state.setSolveTime(System.currentTimeMillis() - startTime);
            solveOrder.add(playerName);
            return true;
        }

        return false;
    }

    private int findEmptyIndex(int[] puzzle) {
        for (int i = 0; i < puzzle.length; i++) {
            if (puzzle[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidMove(int tileIndex, int emptyIndex) {
        if (tileIndex < 0 || tileIndex >= gridSize * gridSize) {
            return false;
        }

        int tileRow = tileIndex / gridSize;
        int tileCol = tileIndex % gridSize;
        int emptyRow = emptyIndex / gridSize;
        int emptyCol = emptyIndex % gridSize;

        // Valid move: adjacent horizontally or vertically (not diagonally)
        return (Math.abs(tileRow - emptyRow) == 1 && tileCol == emptyCol)
                || (Math.abs(tileCol - emptyCol) == 1 && tileRow == emptyRow);
    }

    private boolean isPuzzleSolved(int[] puzzle) {
        for (int i = 0; i < puzzle.length - 1; i++) {
            if (puzzle[i] != i + 1) {
                return false;
            }
        }
        return puzzle[puzzle.length - 1] == 0;
    }

    /** Count how many tiles are in their correct position. */
    public int countCorrectTiles(int[] puzzle) {
        int count = 0;
        for (int i = 0; i < puzzle.length - 1; i++) {
            if (puzzle[i] == i + 1) {
                count++;
            }
        }
        // Empty space is correct if in last position
        if (puzzle[puzzle.length - 1] == 0) {
            count++;
        }
        return count;
    }

    public boolean allPlayersSolved(int playerCount) {
        return solveOrder.size() >= playerCount;
    }

    @Override
    public String calculateWinner() {
        if (solveOrder.isEmpty()) {
            return null;
        }
        return solveOrder.get(0); // First player to solve wins
    }

    @Override
    public boolean hasPlayerAnswered(String playerName) {
        PlayerPuzzleState state = playerStates.get(playerName);
        return state != null && state.isSolved();
    }

    @Override
    public int getAnsweredCount() {
        return solveOrder.size();
    }

    /** Player's puzzle state */
    @Getter
    @Setter
    public static class PlayerPuzzleState {
        private int[] puzzle;
        private int moves;
        private boolean solved;
        private long solveTime; // Time to solve in milliseconds

        public PlayerPuzzleState(int[] puzzle) {
            this.puzzle = puzzle;
            this.moves = 0;
            this.solved = false;
            this.solveTime = 0;
        }

        public void incrementMoves() {
            this.moves++;
        }
    }
}
