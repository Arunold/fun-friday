package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.LevelResult;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.VoteView;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rules of Backseat Drivers.
 *
 * <p>Round {@code n} is level {@code n} (one turn on the level timer). The last round has a second
 * turn: the {@value #VOTE_SECONDS}-second MVP vote. A level is a real-time simulation ({@link
 * LevelRun}: 20 ticks per second, frames streamed to everyone); it ends when the bus parks, touches
 * the CEO's car or the timer runs out. Scoring is team-based: every player still in the game gets
 * the level score.
 *
 * <p>Actions: {@code input} {control, pressed} from the holder of a control (anything else is
 * ignored silently: presses can arrive just after a SEAT SHUFFLE), {@code signal} {kind} from a
 * silent navigator, {@code vote} {playerId} during the MVP vote.
 */
final class BackseatDriversLogic implements GameLogic {

    static final String INPUT = "input";
    static final String SIGNAL = "signal";
    static final String VOTE = "vote";

    /** Length of the MVP vote. */
    static final int VOTE_SECONDS = 20;

    /** Payload of {@value #INPUT}: a key or button of the player's control went down or up. */
    record InputPayload(@NotNull Control control, @NotNull Boolean pressed) {}

    /** Payload of {@value #SIGNAL}. */
    record SignalPayload(@NotNull SignalKind kind) {}

    /** Payload of {@value #VOTE}: the player voted for. */
    record VotePayload(@NotBlank @Size(max = 100) String playerId) {}

    private final Rules rules;
    private final List<TrackMap> campaign;
    private final Seating seating = new Seating();
    private final GameStats stats = new GameStats();
    private final List<LevelResult> results = new ArrayList<>();
    private LevelRun level;
    private MvpVote vote;

    /**
     * Logic for one game.
     *
     * @param rules the host's settings
     * @param campaign the level maps in order (round {@code n} drives map {@code n})
     */
    BackseatDriversLogic(Rules rules, List<TrackMap> campaign) {
        this.rules = rules;
        this.campaign = List.copyOf(campaign);
    }

    // ------------------------------------------------------------------ turns

    /** One turn per level; the last round adds the MVP vote. */
    @Override
    public int turnsInRound(GameContext ctx) {
        return ctx.round() >= ctx.totalRounds() ? 2 : 1;
    }

    @Override
    public void startTurn(GameContext ctx) {
        if (isVoteTurn(ctx)) {
            level = null;
            vote = new MvpVote();
            ctx.setDeadline(Duration.ofSeconds(VOTE_SECONDS));
            return;
        }
        int number = Math.clamp(ctx.round(), 1, campaign.size());
        level = new LevelRun(number, campaign.get(number - 1), rules, seating, stats);
        level.start(ctx);
    }

    private static boolean isVoteTurn(GameContext ctx) {
        return ctx.round() >= ctx.totalRounds() && ctx.turn() == 2;
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        switch (action.type()) {
            case INPUT -> input(ctx, player, action.payload(InputPayload.class));
            case SIGNAL -> signal(ctx, player, action.payload(SignalPayload.class));
            case VOTE -> vote(ctx, player, action.payload(VotePayload.class));
            default -> throw new InvalidActionException("Unknown action " + action.type());
        }
    }

    private void input(GameContext ctx, PlayerInfo player, InputPayload payload) {
        if (payload.control() == Control.NAVIGATOR) {
            throw new InvalidActionException("The navigator has no button — shout instead!");
        }
        if (level != null) {
            level.input(player.id(), payload.control(), payload.pressed(), ctx.now());
        }
        ctx.suppressUpdate();
    }

    private void signal(GameContext ctx, PlayerInfo player, SignalPayload payload) {
        if (rules.variation() != Variation.SILENT_NAVIGATOR) {
            throw new InvalidActionException("Signals are only used by a silent navigator");
        }
        if (level != null) {
            level.signal(ctx, player.id(), payload.kind());
        }
        ctx.suppressUpdate();
    }

    private void vote(GameContext ctx, PlayerInfo player, VotePayload payload) {
        if (vote == null || vote.revealed()) {
            throw new InvalidActionException("The MVP vote starts after the last level");
        }
        if (payload.playerId().equals(player.id())) {
            throw new InvalidActionException("You can't vote for yourself");
        }
        if (ctx.player(payload.playerId()).isEmpty()) {
            throw new InvalidActionException("Pick a player from this game");
        }
        vote.cast(player.id(), payload.playerId());
    }

    @Override
    public void onTimer(GameContext ctx, String key) {
        if (LevelRun.TICK_TIMER.equals(key) && level != null && !level.tick(ctx)) {
            ctx.suppressUpdate();
        }
    }

    @Override
    public void onPlayerLeft(GameContext ctx, PlayerInfo player) {
        if (level != null) {
            level.playerLeft(ctx, player.id());
        }
    }

    // ------------------------------------------------------------------ turn end

    /** Levels end through {@link GameContext#endTurn()} or the timer; the vote when all voted. */
    @Override
    public boolean isTurnComplete(GameContext ctx) {
        if (vote == null || vote.revealed()) {
            return false;
        }
        List<PlayerInfo> voters = voters(ctx);
        return voters.stream().allMatch(p -> vote.hasVoted(p.id()));
    }

    /** Connected players who can vote for somebody (nobody when only one player is left). */
    private static List<PlayerInfo> voters(GameContext ctx) {
        return ctx.players().size() < 2 ? List.of() : ctx.activePlayers();
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        if (vote != null) {
            vote.reveal();
            return TurnOutcome.builder().headline(voteHeadline(ctx)).build();
        }
        if (level == null) {
            return TurnOutcome.builder().build();
        }
        LevelResult result = level.finish(ctx);
        results.add(result);
        TurnOutcome.Builder outcome = TurnOutcome.builder().headline(Scoring.headline(result));
        ctx.players().forEach(p -> outcome.add(p.id(), result.score()));
        return outcome.build();
    }

    private String voteHeadline(GameContext ctx) {
        List<String> winners = vote.winners(playerIds(ctx));
        if (winners.isEmpty()) {
            return "No MVP votes — the whole bus wins!";
        }
        String names =
                winners.stream()
                        .map(id -> ctx.player(id).map(PlayerInfo::name).orElse(id))
                        .collect(Collectors.joining(" & "));
        return (winners.size() == 1 ? "Team MVP: " : "Team MVPs: ") + names + "!";
    }

    // ------------------------------------------------------------------ views

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        if (level == null && vote == null) {
            return null;
        }
        return new BackseatDriversView(
                vote != null ? "VOTE" : "LEVEL",
                rules.variation().key(),
                rules.shuffleSeconds(),
                List.copyOf(results),
                level == null ? null : level.view(viewer),
                vote == null ? null : voteView(ctx, viewer));
    }

    private VoteView voteView(GameContext ctx, Viewer viewer) {
        List<String> ids = playerIds(ctx);
        List<String> voted = vote.voters().stream().filter(ids::contains).toList();
        boolean revealed = vote.revealed();
        return new VoteView(
                revealed,
                ids,
                voted.size(),
                voters(ctx).size(),
                voted,
                viewer.isSpectator() ? null : vote.voteOf(viewer.playerId()),
                revealed ? vote.tally(ids) : null,
                revealed ? vote.winners(ids) : null);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        List<String> ids = playerIds(ctx);
        List<Award> awards = new ArrayList<>();
        if (vote != null) {
            Map<String, Integer> tally = vote.tally(ids);
            for (String winner : vote.winners(ids)) {
                int votes = tally.get(winner);
                awards.add(
                        new Award(
                                "🏆",
                                "Team MVP",
                                winner,
                                votes + (votes == 1 ? " vote" : " votes")));
            }
        }
        awards.addAll(stats.awards(ids));
        return awards;
    }

    /** Bus, roles, hazards and the vote, for end-to-end tests. */
    @Override
    public Object debugState(GameContext ctx) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", vote != null ? "VOTE" : "LEVEL");
        state.put("results", List.copyOf(results));
        if (level != null) {
            state.putAll(DebugState.of(ctx, level, seating));
        }
        if (vote != null) {
            Map<String, String> votes = new LinkedHashMap<>();
            vote.voters().forEach(id -> votes.put(id, vote.voteOf(id)));
            state.put("votes", votes);
        }
        return state;
    }

    private static List<String> playerIds(GameContext ctx) {
        return ctx.players().stream().map(PlayerInfo::id).toList();
    }
}
