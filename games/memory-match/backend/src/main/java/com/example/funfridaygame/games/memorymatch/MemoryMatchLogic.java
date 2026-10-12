package com.example.funfridaygame.games.memorymatch;

import com.example.funfridaygame.games.memorymatch.Board.CardState;
import com.example.funfridaygame.games.memorymatch.MemoryMatchView.MoveResult;
import com.example.funfridaygame.games.memorymatch.MemoryMatchView.PlayerProgress;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rules of Memory Match. A round is one platform turn: one shared board that the players work
 * through move by move, in turn order, until every pair is found.
 *
 * <p>A move: flip a first card, then a second. A pair stays face up in the finder's colour and the
 * finder moves again. Otherwise both cards stay visible for {@link #FLIP_BACK} so everyone can
 * memorise them, then flip back and the next connected player moves. A move that isn't finished
 * within the move time is forfeited. Players who are disconnected or gone are skipped at once.
 */
final class MemoryMatchLogic implements GameLogic {

    static final String FLIP = "flip";
    static final String MOVE_TIMER = "move";
    static final String FLIP_BACK_TIMER = "flip-back";
    static final Duration FLIP_BACK = Duration.ofMillis(1200);
    static final Duration MAX_ROUND = Duration.ofMinutes(10);
    private static final Duration MIN_TIME_PER_PAIR = Duration.ofSeconds(20);

    private final BoardSize size;
    private final Duration moveTime;
    private final CardTheme theme;
    private final TurnOrder turns = new TurnOrder();
    private final Scorebook scores = new Scorebook();

    private Board board;
    private Step step = Step.OVER;
    private int first = -1;
    private int second = -1;
    private int streak;
    private Long moveDeadline;
    private MoveResult last;
    private int results;
    private boolean revealed;

    /** Payload of the {@value #FLIP} action. */
    record Flip(@NotNull @Min(0) Integer index) {}

    /** Where the current move stands. */
    private enum Step {
        /** Waiting for the mover's first card. */
        FIRST,
        /** One card is up; waiting for the second. */
        SECOND,
        /** A miss is shown; the cards flip back when {@link #FLIP_BACK_TIMER} fires. */
        RESOLVING,
        /** The board is finished. */
        OVER
    }

    /** What a move ended with (see {@link MoveResult#kind()}). */
    private enum Kind {
        MATCH,
        MISS,
        TIMEOUT,
        AWAY
    }

    MemoryMatchLogic(BoardSize size, Duration moveTime, CardTheme theme) {
        this.size = size;
        this.moveTime = moveTime;
        this.theme = theme;
    }

    // ------------------------------------------------------------------ round

    @Override
    public void startTurn(GameContext ctx) {
        // Reset everything the view shows before the first view of the round is rendered.
        revealed = false;
        board = Board.deal(size, theme, ctx.random());
        scores.startRound();
        last = null;
        streak = 0;
        ctx.setDeadline(roundLength());
        turns.startRound(ctx);
        beginMove(ctx);
    }

    /**
     * The safety-net length of a round (moves have their own timer): per pair twice the move time
     * but at least 20 s, at most {@link #MAX_ROUND} in total.
     */
    Duration roundLength() {
        Duration perPair = moveTime.multipliedBy(2);
        if (perPair.compareTo(MIN_TIME_PER_PAIR) < 0) {
            perPair = MIN_TIME_PER_PAIR;
        }
        Duration total = perPair.multipliedBy(size.pairs());
        return total.compareTo(MAX_ROUND) > 0 ? MAX_ROUND : total;
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        if (board.cleared() || ctx.activePlayers().isEmpty()) {
            return true;
        }
        // The platform has no connection callback, but it asks this after every roster change
        // (including disconnects), so this is where a disconnected mover is skipped at once.
        handOverIfMoverAway(ctx);
        return false;
    }

    @Override
    public void onPlayerLeft(GameContext ctx, PlayerInfo player) {
        handOverIfMoverAway(ctx);
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        stopMoving(ctx);
        revealed = true;
        Set<String> leaders = scores.mostPairs(ctx);
        TurnOutcome.Builder outcome = TurnOutcome.builder().headline(headline(ctx, leaders));
        for (PlayerInfo player : ctx.players()) {
            int bonus = leaders.contains(player.id()) ? Scorebook.MOST_PAIRS_BONUS : 0;
            int points = scores.roundPoints(player.id()) + bonus;
            if (points > 0) {
                outcome.add(player.id(), points);
            }
        }
        return outcome.build();
    }

    // ------------------------------------------------------------------ moves

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        if (!FLIP.equals(action.type())) {
            throw new InvalidActionException("Unknown action " + action.type());
        }
        int index = action.payload(Flip.class).index();
        handOverIfMoverAway(ctx);
        checkTurn(ctx, player);
        checkCard(index);
        boolean seenBefore = board.flip(index);
        if (step == Step.FIRST) {
            first = index;
            step = Step.SECOND;
            return;
        }
        second = index;
        if (board.sameFace(first, second)) {
            onMatch(ctx, player.id(), !seenBefore);
        } else {
            onMiss(ctx, player.id());
        }
    }

    @Override
    public void onTimer(GameContext ctx, String key) {
        if (FLIP_BACK_TIMER.equals(key) && step == Step.RESOLVING) {
            board.hide(first);
            board.hide(second);
            passMove(ctx);
        } else if (MOVE_TIMER.equals(key) && (step == Step.FIRST || step == Step.SECOND)) {
            forfeit(ctx, Kind.TIMEOUT);
        }
    }

    /** Rejects a flip by anyone but the mover, after the board, or while a miss is shown. */
    private void checkTurn(GameContext ctx, PlayerInfo player) {
        if (step == Step.OVER) {
            throw new InvalidActionException("This board is finished");
        }
        if (!player.id().equals(turns.mover())) {
            String name = ctx.player(turns.mover()).map(PlayerInfo::name).orElse("someone else");
            throw new InvalidActionException("It's " + name + "'s turn");
        }
        if (step == Step.RESOLVING) {
            throw new InvalidActionException("Wait for the cards to flip back");
        }
    }

    /** Rejects cards that don't exist, are already found or are already face up. */
    private void checkCard(int index) {
        if (!board.contains(index)) {
            throw new InvalidActionException("There is no card " + index);
        }
        CardState state = board.state(index);
        if (state == CardState.MATCHED) {
            throw new InvalidActionException("That pair has already been found");
        }
        if (state == CardState.UP) {
            throw new InvalidActionException("That card is already face up");
        }
    }

    /** A pair: it stays up in the finder's colour and the finder moves again. */
    private void onMatch(GameContext ctx, String playerId, boolean lucky) {
        board.match(first, second, playerId);
        streak++;
        int points = scores.pair(playerId, streak, lucky);
        last = result(Kind.MATCH, playerId, List.of(first, second), points);
        if (board.cleared()) {
            stopMoving(ctx);
        } else {
            beginMove(ctx);
        }
    }

    /**
     * No pair: the streak ends, both cards stay visible for a moment, then the next player moves.
     */
    private void onMiss(GameContext ctx, String playerId) {
        streak = 0;
        last = result(Kind.MISS, playerId, List.of(first, second), 0);
        step = Step.RESOLVING;
        moveDeadline = null;
        ctx.cancel(MOVE_TIMER);
        ctx.schedule(FLIP_BACK_TIMER, FLIP_BACK);
    }

    /** Ends a move without a pair: a single face-up card turns back and the next player moves. */
    private void forfeit(GameContext ctx, Kind kind) {
        List<Integer> cards = first >= 0 ? List.of(first) : List.of();
        board.hide(first);
        last = result(kind, turns.mover(), cards, 0);
        passMove(ctx);
    }

    /** The streak ends and the next connected player in the order moves. */
    private void passMove(GameContext ctx) {
        streak = 0;
        turns.pass(ctx);
        beginMove(ctx);
    }

    /** Starts a move of the current mover with a fresh move timer. */
    private void beginMove(GameContext ctx) {
        first = -1;
        second = -1;
        step = Step.FIRST;
        String mover = turns.mover();
        if (mover == null || !ctx.isActive(mover)) {
            // Nobody is connected: isTurnComplete ends the round.
            moveDeadline = null;
            ctx.cancel(MOVE_TIMER);
            return;
        }
        moveDeadline = ctx.now() + moveTime.toMillis();
        ctx.schedule(MOVE_TIMER, moveTime);
    }

    /** The board is finished: no more moves, no more move timers. */
    private void stopMoving(GameContext ctx) {
        step = Step.OVER;
        moveDeadline = null;
        ctx.cancel(MOVE_TIMER);
        ctx.cancel(FLIP_BACK_TIMER);
    }

    /**
     * Passes the move on when the mover disconnected or left in the middle of it. While a miss is
     * shown nothing needs to happen: the flip-back timer passes the move to a connected player.
     */
    private void handOverIfMoverAway(GameContext ctx) {
        if (step != Step.FIRST && step != Step.SECOND) {
            return;
        }
        String mover = turns.mover();
        if ((mover != null && ctx.isActive(mover)) || ctx.activePlayers().isEmpty()) {
            return;
        }
        if (mover == null) {
            turns.pass(ctx);
            beginMove(ctx);
        } else {
            forfeit(ctx, Kind.AWAY);
        }
    }

    /** The next move result (numbered, so clients can tell a new result from a re-sent one). */
    private MoveResult result(Kind kind, String playerId, List<Integer> cards, int points) {
        results++;
        return new MoveResult(
                results, kind.name(), playerId, cards, points, kind == Kind.MATCH ? streak : 0);
    }

    // ------------------------------------------------------------------ views

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        // Everyone sees the same board; hidden faces never leave the server (Board.cardViews).
        Set<String> leaders = revealed ? scores.mostPairs(ctx) : Set.of();
        List<PlayerProgress> players = new ArrayList<>();
        for (String playerId : turns.order()) {
            if (ctx.player(playerId).isPresent()) {
                players.add(
                        new PlayerProgress(
                                playerId,
                                scores.roundPairs(playerId),
                                scores.roundPoints(playerId),
                                revealed ? leaders.contains(playerId) : null));
            }
        }
        boolean moving = step != Step.OVER && !revealed;
        return new MemoryMatchView(
                size.rows(),
                size.cols(),
                board.cardViews(revealed),
                moving ? turns.mover() : null,
                moving ? moveDeadline : null,
                moveTime.toMillis(),
                moving && step == Step.RESOLVING,
                board.pairsLeft(),
                size.pairs(),
                moving ? streak : 0,
                players,
                last,
                revealed);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return scores.awards(ctx);
    }

    @Override
    public Object debugState(GameContext ctx) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (board == null) {
            return state;
        }
        String mover = step == Step.OVER ? null : turns.mover();
        state.put("faces", board.faces());
        state.put("pairs", board.unmatchedPairs());
        state.put("activePlayerId", mover);
        state.put("activePlayerName", ctx.player(mover).map(PlayerInfo::name).orElse(null));
        state.put("step", step.name());
        return state;
    }

    /**
     * The reveal line: how the round ended ("Board cleared!", "Time's up!" or "Round over!" when
     * nobody was connected any more) and who found the most pairs.
     */
    private String headline(GameContext ctx, Set<String> leaders) {
        String prefix =
                board.cleared()
                        ? "Board cleared! "
                        : ctx.remainingMillis() == 0 ? "Time's up! " : "Round over! ";
        if (leaders.isEmpty()) {
            return prefix + "Nobody found a pair.";
        }
        int pairs = scores.roundPairs(leaders.iterator().next());
        List<String> names =
                leaders.stream()
                        .map(id -> ctx.player(id).map(PlayerInfo::name).orElse("?"))
                        .toList();
        if (names.size() == 1) {
            return prefix + names.getFirst() + " found the most pairs (" + pairs + ")";
        }
        String joined =
                String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
        return prefix + joined + " tie with " + pairs + " pairs each";
    }
}
