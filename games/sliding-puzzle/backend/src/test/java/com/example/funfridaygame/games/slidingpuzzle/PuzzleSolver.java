package com.example.funfridaygame.games.slidingpuzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Breadth-first solver for 3×3 boards, so scenario tests can play real solves. */
final class PuzzleSolver {

    private PuzzleSolver() {}

    /**
     * A shortest solution.
     *
     * @param cells the board in reading order, {@code 0} = gap
     * @return the tiles to push, each one cell into the gap
     */
    static List<Integer> solve(List<Integer> cells, int size) {
        Board start = Board.of(size, cells.stream().mapToInt(Integer::intValue).toArray());
        long goal = encode(Board.solved(size));
        long first = encode(start);
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Integer> tileMoved = new HashMap<>();
        ArrayDeque<Board> queue = new ArrayDeque<>();
        parent.put(first, first);
        queue.add(start);
        while (!queue.isEmpty()) {
            Board board = queue.poll();
            long key = encode(board);
            if (key == goal) {
                return path(key, first, parent, tileMoved);
            }
            for (int cell : board.gapNeighbours()) {
                Board next = board.copy();
                int tile = next.tileAt(cell);
                next.moveIntoGap(cell);
                long nextKey = encode(next);
                if (!parent.containsKey(nextKey)) {
                    parent.put(nextKey, key);
                    tileMoved.put(nextKey, tile);
                    queue.add(next);
                }
            }
        }
        throw new IllegalArgumentException("Unsolvable board " + cells);
    }

    private static List<Integer> path(
            long goal, long first, Map<Long, Long> parent, Map<Long, Integer> tileMoved) {
        List<Integer> tiles = new ArrayList<>();
        for (long key = goal; key != first; key = parent.get(key)) {
            tiles.add(tileMoved.get(key));
        }
        Collections.reverse(tiles);
        return tiles;
    }

    private static long encode(Board board) {
        long key = 0;
        int cells = board.size() * board.size();
        for (int i = 0; i < cells; i++) {
            key = key << 4 | board.tileAt(i);
        }
        return key;
    }
}
