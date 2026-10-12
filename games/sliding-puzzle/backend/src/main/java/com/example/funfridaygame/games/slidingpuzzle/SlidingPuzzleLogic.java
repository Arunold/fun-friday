package com.example.funfridaygame.games.slidingpuzzle;

import com.example.funfridaygame.games.slidingpuzzle.SlidingPuzzleView.PlayerStatus;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules of Sliding Puzzle. One turn per round: everyone gets their own copy of the same scramble
 * and races to solve it; the round ends when every connected player solved it or time is up. See
 * {@link Scoring} for the points.
 *
 * <p>Browsers apply moves optimistically and send {@code move} actions; the server validates and
 * applies each one to the player's own board, which every view carries back so a client that
 * diverged re-syncs.
 */
final class SlidingPuzzleLogic implements GameLogic {

    static final String MOVE = "move";

    private final int size;
    private final Duration timeLimit;
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();
    private final SolveStats stats = new SolveStats();
    private Board start;
    private int scrambleDistance;
    private boolean revealed;

    /** Payload of the {@value #MOVE} action: the tile to push toward the gap. */
    record Move(@NotNull @Min(1) Integer tile) {}

    /**
     * @param size cells per row
     * @param timeLimitSeconds length of a round, already resolved for "Auto"
     */
    SlidingPuzzleLogic(int size, int timeLimitSeconds) {
        this.size = size;
        this.timeLimit = Duration.ofSeconds(timeLimitSeconds);
        this.start = Board.solved(size);
    }

    @Override
    public void startTurn(GameContext ctx) {
        start = Scrambler.scramble(size, ctx.random());
        scrambleDistance = start.manhattanDistance();
        revealed = false;
        attempts.clear();
        for (PlayerInfo player : ctx.players()) {
            attempts.put(player.id(), new Attempt(start.copy()));
        }
        ctx.setDeadline(timeLimit);
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!MOVE.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        int tile = action.payload(Move.class).tile();
        if (revealed) {
            throw new InvalidActionException("This round is over");
        }
        if (tile >= size * size) {
            throw new InvalidActionException("There is no tile " + tile + " on this board");
        }
        Attempt mine = attempts.computeIfAbsent(player.id(), id -> new Attempt(start.copy()));
        if (mine.solved()) {
            throw new InvalidActionException("You already solved this puzzle");
        }
        if (mine.slide(tile, ctx.now() - ctx.turnStartedAt()) == 0) {
            throw new InvalidActionException(
                    "Tile " + tile + " can't move — pick one in the gap's row or column");
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(p -> solved(p.id()));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        Map<String, Integer> ranks = ranks(ctx);
        TurnOutcome.Builder outcome = TurnOutcome.builder().headline(headline(ctx, ranks));
        for (PlayerInfo player : ctx.players()) {
            Attempt attempt = attempts.get(player.id());
            if (attempt == null) {
                continue;
            }
            stats.record(player.id(), attempt);
            int points =
                    Scoring.points(attempt, ranks.getOrDefault(player.id(), 0), scrambleDistance);
            if (points > 0) {
                outcome.add(player.id(), points);
            }
        }
        return outcome.build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        Map<String, Integer> ranks = ranks(ctx);
        List<PlayerStatus> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            Attempt attempt = attempts.get(player.id());
            if (attempt != null) {
                players.add(status(player.id(), attempt, ranks.get(player.id())));
            }
        }
        Attempt mine = viewer.isSpectator() ? null : attempts.get(viewer.playerId());
        return new SlidingPuzzleView(
                ctx.round(),
                size,
                revealed,
                start.cells(),
                mine == null ? null : mine.board().cells(),
                mine == null ? null : mine.moves(),
                mine == null ? null : mine.solved(),
                mine == null ? null : ranks.get(viewer.playerId()),
                mine == null || !mine.solved() ? null : mine.solvedAfterMillis(),
                players);
    }

    @Override
    public void onPlayerLeft(GameContext ctx, PlayerInfo player) {
        attempts.remove(player.id());
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return stats.awards();
    }

    /**
     * For end-to-end tests: the scramble and every player's current board (by name and id), so a
     * test can compute a solution.
     */
    @Override
    public Object debugState(GameContext ctx) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            Attempt attempt = attempts.get(player.id());
            if (attempt != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", player.id());
                entry.put("name", player.name());
                entry.put("board", attempt.board().cells());
                entry.put("moves", attempt.moves());
                entry.put("solved", attempt.solved());
                players.add(entry);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("size", size);
        state.put("start", start.cells());
        state.put("scrambleDistance", scrambleDistance);
        state.put("timeLimitSeconds", timeLimit.toSeconds());
        state.put("players", players);
        return state;
    }

    private boolean solved(String playerId) {
        Attempt attempt = attempts.get(playerId);
        return attempt != null && attempt.solved();
    }

    /**
     * Finishing positions of the solvers still in the game: 1 + the number of faster solvers, so
     * equal times share a place and a departed winner moves everyone up.
     */
    private Map<String, Integer> ranks(GameContext ctx) {
        List<PlayerInfo> players = ctx.players();
        Map<String, Integer> ranks = new HashMap<>();
        for (PlayerInfo player : players) {
            Attempt attempt = attempts.get(player.id());
            if (attempt == null || !attempt.solved()) {
                continue;
            }
            int rank = 1;
            for (PlayerInfo other : players) {
                Attempt theirs = attempts.get(other.id());
                if (theirs != null
                        && theirs.solved()
                        && theirs.solvedAfterMillis() < attempt.solvedAfterMillis()) {
                    rank++;
                }
            }
            ranks.put(player.id(), rank);
        }
        return ranks;
    }

    /** A player's public progress: everything but the board. */
    private static PlayerStatus status(String playerId, Attempt attempt, Integer rank) {
        return new PlayerStatus(
                playerId,
                attempt.board().tilesInPlace(),
                attempt.progressPercent(),
                attempt.moves(),
                attempt.solved(),
                rank,
                attempt.solved() ? attempt.solvedAfterMillis() : null);
    }

    /**
     * The reveal headline: the fastest solver(s) and their time, otherwise who got closest (among
     * players who moved), otherwise a consolation line.
     */
    private String headline(GameContext ctx, Map<String, Integer> ranks) {
        List<String> winners = new ArrayList<>();
        long winningTime = 0;
        String closest = null;
        int closestProgress = -1;
        for (PlayerInfo player : ctx.players()) {
            Attempt attempt = attempts.get(player.id());
            if (attempt == null) {
                continue;
            }
            if (Integer.valueOf(1).equals(ranks.get(player.id()))) {
                winners.add(player.name());
                winningTime = attempt.solvedAfterMillis();
            } else if (!attempt.solved()
                    && attempt.moves() > 0
                    && attempt.progressPercent() > closestProgress) {
                closest = player.name();
                closestProgress = attempt.progressPercent();
            }
        }
        if (!winners.isEmpty()) {
            return String.join(" and ", winners)
                    + " solved it first in "
                    + Scoring.formatTime(winningTime)
                    + "!";
        }
        if (closest != null) {
            return "Nobody solved it — "
                    + closest
                    + " got closest ("
                    + closestProgress
                    + "% of tiles in place)";
        }
        return "Nobody solved it this time";
    }
}
