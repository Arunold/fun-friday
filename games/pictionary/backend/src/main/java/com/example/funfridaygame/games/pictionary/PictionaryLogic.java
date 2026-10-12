package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.games.pictionary.PictionaryView.ChoiceView;
import com.example.funfridaygame.games.pictionary.PictionaryView.CloseEvent;
import com.example.funfridaygame.games.pictionary.PictionaryView.CorrectView;
import com.example.funfridaygame.games.pictionary.PictionaryView.EditEvent;
import com.example.funfridaygame.games.pictionary.PictionaryView.FeedEntryView;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Rules of Pictionary.
 *
 * <p>Every round each player draws once, in the join order fixed when the round starts; drawers who
 * left or are offline are skipped (BUG-04). A turn starts with a {@value #CHOICE_SECONDS}-second
 * word choice — one of three words, picked at random when time runs out — followed by the drawing
 * phase on the round timer. The turn ends early once every connected guesser has the word, and
 * right away, without points, when the drawer leaves or disconnects.
 *
 * <p>Drawing actions are appended to the turn's {@link Drawing} log and relayed with {@link
 * GameContext#stream} without a snapshot ({@link GameContext#suppressUpdate()}); guesses, hints and
 * phase changes produce normal snapshots, which carry the whole log and so re-sync every canvas.
 */
final class PictionaryLogic implements GameLogic {

    static final String CHOOSE = "choose";
    static final String STROKE = "stroke";
    static final String FILL = "fill";
    static final String UNDO = "undo";
    static final String REDO = "redo";
    static final String CLEAR = "clear";
    static final String GUESS = "guess";
    static final String SYNC = "sync";

    /** Stream event carrying a stroke batch. */
    static final String STROKE_EVENT = "stroke";

    /** Stream event for fills, undo, redo and clear. */
    static final String EDIT_EVENT = "edit";

    /** Private event answering {@value #SYNC} with the full drawing. */
    static final String SYNC_EVENT = "sync";

    /** Private event for a near-miss guess ("So close!"). */
    static final String CLOSE_EVENT = "close";

    /** Length of the word choice. */
    static final int CHOICE_SECONDS = 10;

    /** Guesses per player per turn. */
    static final int MAX_GUESSES = 30;

    /** Feed lines per view. */
    static final int FEED_LIMIT = 50;

    private static final Duration CHOICE_TIME = Duration.ofSeconds(CHOICE_SECONDS);

    /** The auto-pick fires just before the choice deadline, so a turn never ends unpicked. */
    private static final Duration AUTO_PICK_DELAY = CHOICE_TIME.minusMillis(100);

    private static final String AUTO_PICK = "auto-pick";
    private static final String FIRST_HINT = "hint-1";
    private static final String SECOND_HINT = "hint-2";
    private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

    /** Payload of {@value #CHOOSE}: index of the chosen word. */
    record ChoosePayload(@NotNull @Min(0) @Max(2) Integer index) {}

    /** Payload of {@value #STROKE}: a batch of points {@code x1, y1, x2, y2, …} of one stroke. */
    record StrokePayload(
            @Min(1) int id,
            @NotNull @Pattern(regexp = "pen|eraser") String tool,
            @NotNull @Pattern(regexp = HEX_COLOR) String color,
            @Min(1) @Max(Drawing.MAX_SIZE) int size,
            @NotNull @Size(min = 2, max = 2 * Drawing.MAX_BATCH_POINTS) int[] points) {}

    /** Payload of {@value #FILL}: a bucket fill at a point. */
    record FillPayload(
            @Min(1) int id,
            @NotNull @Pattern(regexp = HEX_COLOR) String color,
            @Min(0) @Max(Drawing.WIDTH - 1) int x,
            @Min(0) @Max(Drawing.HEIGHT - 1) int y) {}

    /** Payload of {@value #GUESS}. */
    record GuessPayload(@NotBlank @Size(max = 60) String text) {}

    private final long drawMillis;
    private final boolean hints;
    private final WordPicker words;
    private final GameStats stats = new GameStats();
    private List<String> drawerOrder = List.of();
    private int turnCount;
    private Turn turn;

    /**
     * Logic for one game.
     *
     * @param drawTime length of the drawing phase
     * @param words the game's word picker
     * @param hints whether letters are revealed at half and three quarters of the time
     */
    PictionaryLogic(Duration drawTime, WordPicker words, boolean hints) {
        this.drawMillis = drawTime.toMillis();
        this.words = words;
        this.hints = hints;
    }

    // ------------------------------------------------------------------ turn order

    /** One turn per player; the drawer order is fixed for the whole round. */
    @Override
    public int turnsInRound(GameContext ctx) {
        drawerOrder = ctx.players().stream().map(PlayerInfo::id).toList();
        return drawerOrder.size();
    }

    /** Skips drawers who left or are offline, and every turn once nobody is left to guess. */
    @Override
    public boolean skipTurn(GameContext ctx) {
        String drawerId = drawerOf(ctx);
        return drawerId == null || !ctx.isActive(drawerId) || ctx.players().size() < 2;
    }

    @Override
    public void startTurn(GameContext ctx) {
        turn = null;
        String drawerId = drawerOf(ctx);
        Optional<PlayerInfo> drawer = drawerId == null ? Optional.empty() : ctx.player(drawerId);
        if (drawer.isEmpty()) {
            ctx.endTurn(); // cannot happen: skipTurn checked the drawer just before
            return;
        }
        turn = new Turn(++turnCount, drawer.get(), words.next(ctx.random()));
        ctx.setDeadline(CHOICE_TIME);
        ctx.schedule(AUTO_PICK, AUTO_PICK_DELAY);
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        Turn current = turn;
        if (current == null || current.revealed()) {
            throw new InvalidActionException("Wait for the next turn");
        }
        switch (action.type()) {
            case CHOOSE -> choose(ctx, current, player, action.payload(ChoosePayload.class));
            case STROKE -> stroke(ctx, current, player, action.payload(StrokePayload.class));
            case FILL -> fill(ctx, current, player, action.payload(FillPayload.class));
            case UNDO -> edit(ctx, current, player, Drawing::undo);
            case REDO -> edit(ctx, current, player, Drawing::redo);
            case CLEAR -> edit(ctx, current, player, Drawing::clear);
            case GUESS -> guess(ctx, current, player, action.payload(GuessPayload.class));
            case SYNC -> {
                ctx.sendTo(player.id(), SYNC_EVENT, current.drawing().view());
                ctx.suppressUpdate();
            }
            default -> throw new InvalidActionException("Unknown action " + action.type());
        }
    }

    private void choose(GameContext ctx, Turn current, PlayerInfo player, ChoosePayload payload) {
        if (!current.isDrawer(player.id())) {
            throw new InvalidActionException(current.drawer().name() + " picks the word");
        }
        if (current.phase() != Turn.Phase.CHOOSING) {
            throw new InvalidActionException("The word is already picked");
        }
        startDrawing(ctx, current, current.choices().get(payload.index()));
    }

    private void startDrawing(GameContext ctx, Turn current, WordPicker.Choice choice) {
        current.startDrawing(choice, ctx.now(), ctx.random());
        words.markChosen(choice.word());
        ctx.cancel(AUTO_PICK);
        ctx.setDeadline(Duration.ofMillis(drawMillis));
        if (hints && current.mask().firstHintLetters() > 0) {
            ctx.schedule(FIRST_HINT, Duration.ofMillis(drawMillis / 2));
        }
        if (hints && current.mask().secondHintLetters() > 0) {
            ctx.schedule(SECOND_HINT, Duration.ofMillis(drawMillis * 3 / 4));
        }
    }

    private void stroke(GameContext ctx, Turn current, PlayerInfo player, StrokePayload payload) {
        requireDrawer(current, player);
        current.drawing()
                .stroke(
                        payload.id(),
                        Drawing.Tool.fromKey(payload.tool()),
                        payload.color(),
                        payload.size(),
                        payload.points())
                .ifPresent(event -> ctx.stream(STROKE_EVENT, event));
        ctx.suppressUpdate();
    }

    private void fill(GameContext ctx, Turn current, PlayerInfo player, FillPayload payload) {
        requireDrawer(current, player);
        current.drawing()
                .fill(payload.id(), payload.color(), payload.x(), payload.y())
                .ifPresent(event -> ctx.stream(EDIT_EVENT, event));
        ctx.suppressUpdate();
    }

    private void edit(
            GameContext ctx,
            Turn current,
            PlayerInfo player,
            Function<Drawing, Optional<EditEvent>> change) {
        requireDrawer(current, player);
        change.apply(current.drawing()).ifPresent(event -> ctx.stream(EDIT_EVENT, event));
        ctx.suppressUpdate();
    }

    private void guess(GameContext ctx, Turn current, PlayerInfo player, GuessPayload payload) {
        if (current.isDrawer(player.id())) {
            throw new InvalidActionException("You're drawing — no guessing!");
        }
        if (current.phase() != Turn.Phase.DRAWING) {
            throw new InvalidActionException(
                    "Wait until " + current.drawer().name() + " has picked a word");
        }
        GuessBook book = current.guesses();
        String text = book.register(player.id(), payload.text(), MAX_GUESSES);
        switch (WordMatcher.judge(text, current.word())) {
            case CORRECT -> {
                long elapsed = Math.max(0, ctx.now() - current.drawStartedAt());
                book.addCorrect(
                        player, elapsed, Scoring.guesser(elapsed, drawMillis, book.noneCorrect()));
            }
            case CLOSE -> {
                book.addClose(player, text);
                ctx.sendTo(player.id(), CLOSE_EVENT, new CloseEvent(text));
            }
            default -> book.addWrong(player, text);
        }
    }

    private static void requireDrawer(Turn current, PlayerInfo player) {
        if (!current.isDrawer(player.id())) {
            throw new InvalidActionException("It's " + current.drawer().name() + "'s turn to draw");
        }
        if (current.phase() != Turn.Phase.DRAWING) {
            throw new InvalidActionException("Pick a word first");
        }
    }

    // ------------------------------------------------------------------ timers & turn end

    @Override
    public void onTimer(GameContext ctx, String key) {
        Turn current = turn;
        if (current == null) {
            return;
        }
        switch (key) {
            case AUTO_PICK -> {
                if (current.phase() == Turn.Phase.CHOOSING) {
                    List<WordPicker.Choice> choices = current.choices();
                    startDrawing(ctx, current, choices.get(ctx.random().nextInt(choices.size())));
                }
            }
            case FIRST_HINT -> current.mask().reveal(current.mask().firstHintLetters());
            case SECOND_HINT -> current.mask().reveal(current.mask().secondHintLetters());
            default -> {
                // not one of ours
            }
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        Turn current = turn;
        if (current == null || current.revealed()) {
            return false;
        }
        if (!ctx.isActive(current.drawer().id()) || ctx.players().size() < 2) {
            return true;
        }
        return current.phase() == Turn.Phase.DRAWING && everyoneGuessed(ctx, current);
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        Turn current = turn;
        if (current == null) {
            return TurnOutcome.builder().headline("Skipped").build();
        }
        Turn.End end = endReason(ctx, current);
        TurnOutcome.Builder outcome = TurnOutcome.builder();
        if (end.voids()) {
            current.finish(end, 0);
            return outcome.headline(voidHeadline(current, end)).build();
        }
        List<GuessBook.Correct> correct = current.guesses().correct();
        correct.forEach(c -> outcome.add(c.playerId(), c.points()));
        int drawerPoints = Scoring.drawer(correct.size());
        if (drawerPoints > 0) {
            outcome.add(current.drawer().id(), drawerPoints);
        }
        stats.recordTurn(current.drawer().id(), correct);
        current.finish(end, drawerPoints);
        String reveal = "The word was “" + current.word() + "”";
        return outcome.headline(
                        end == Turn.End.NO_GUESSERS ? "Everybody else left — " + reveal : reveal)
                .build();
    }

    private static Turn.End endReason(GameContext ctx, Turn current) {
        String drawerId = current.drawer().id();
        if (ctx.player(drawerId).isEmpty()) {
            return Turn.End.DRAWER_LEFT;
        }
        if (!ctx.isActive(drawerId)) {
            return Turn.End.DRAWER_OFFLINE;
        }
        if (ctx.players().size() < 2) {
            return Turn.End.NO_GUESSERS;
        }
        return current.phase() == Turn.Phase.DRAWING && everyoneGuessed(ctx, current)
                ? Turn.End.ALL_GUESSED
                : Turn.End.TIME_UP;
    }

    private static String voidHeadline(Turn current, Turn.End end) {
        String name = current.drawer().name();
        String what =
                end == Turn.End.DRAWER_LEFT
                        ? name + " left the game"
                        : name + " lost the connection";
        return current.word() == null
                ? what + " before picking a word — no points this turn"
                : what + " — no points this turn. The word was “" + current.word() + "”";
    }

    /** At least one connected guesser, and all connected guessers got the word. */
    private static boolean everyoneGuessed(GameContext ctx, Turn current) {
        List<PlayerInfo> guessers =
                ctx.activePlayers().stream().filter(p -> !current.isDrawer(p.id())).toList();
        return !guessers.isEmpty()
                && guessers.stream().allMatch(p -> current.guesses().hasGuessed(p.id()));
    }

    // ------------------------------------------------------------------ views

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        Turn current = turn;
        if (current == null) {
            return null;
        }
        String me = viewer.playerId();
        boolean drawer = viewer.is(current.drawer().id());
        boolean guesser = me != null && !drawer;
        boolean revealed = current.revealed();
        boolean drawing = current.phase() == Turn.Phase.DRAWING;
        GuessBook book = current.guesses();
        boolean guessed = guesser && book.hasGuessed(me);
        boolean seesWord = drawing && (revealed || drawer || guessed);
        return new PictionaryView(
                current.key(),
                current.phase().name(),
                revealed,
                current.drawer().id(),
                current.drawer().name(),
                drawer && !drawing && !revealed ? choiceViews(current) : null,
                seesWord ? current.word() : null,
                drawing ? current.mask().masked() : null,
                guesser ? guessed : null,
                guesser && drawing && !guessed && !revealed
                        ? book.guessesLeft(me, MAX_GUESSES)
                        : null,
                book.feedFor(me, FEED_LIMIT).stream().map(PictionaryLogic::feedView).toList(),
                book.correct().stream().map(c -> correctView(current, c)).toList(),
                guesserCount(ctx, current),
                current.drawing().view(),
                revealed ? current.end().name() : null,
                revealed ? current.drawerPoints() : null);
    }

    /** Players in the game other than the drawer. */
    private static int guesserCount(GameContext ctx, Turn current) {
        return (int) ctx.players().stream().filter(p -> !current.isDrawer(p.id())).count();
    }

    private static List<ChoiceView> choiceViews(Turn current) {
        return current.choices().stream()
                .map(c -> new ChoiceView(c.word(), c.level().name()))
                .toList();
    }

    private static FeedEntryView feedView(GuessBook.Entry entry) {
        return new FeedEntryView(
                entry.id(), entry.playerId(), entry.name(), entry.kind().name(), entry.text());
    }

    private static CorrectView correctView(Turn current, GuessBook.Correct correct) {
        Integer points = null;
        if (current.revealed()) {
            points = current.end().voids() ? 0 : correct.points();
        }
        return new CorrectView(correct.playerId(), correct.name(), correct.elapsedMillis(), points);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return stats.awards();
    }

    /** The word, the drawer and the choices, for end-to-end tests. */
    @Override
    public Object debugState(GameContext ctx) {
        Map<String, Object> state = new LinkedHashMap<>();
        Turn current = turn;
        if (current != null) {
            state.put("turn", current.key());
            state.put("phase", current.phase().name());
            state.put("drawerId", current.drawer().id());
            state.put("drawerName", current.drawer().name());
            state.put("word", current.word());
            state.put("choices", current.choices().stream().map(WordPicker.Choice::word).toList());
            state.put("version", current.drawing().version());
            state.put("ops", current.drawing().visibleOps());
        }
        return state;
    }

    private String drawerOf(GameContext ctx) {
        int index = ctx.turn() - 1;
        return index >= 0 && index < drawerOrder.size() ? drawerOrder.get(index) : null;
    }
}
