package com.example.funfridaygame.games.wordscramble;

import com.example.funfridaygame.games.wordscramble.WordScrambleView.HintLetter;
import com.example.funfridaygame.games.wordscramble.WordScrambleView.MyStatus;
import com.example.funfridaygame.games.wordscramble.WordScrambleView.PlayerStatus;
import com.example.funfridaygame.sdk.Action;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.sdk.PlayerInfo;
import com.example.funfridaygame.sdk.TurnOutcome;
import com.example.funfridaygame.sdk.Viewer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Rules of Word Scramble. Every round everyone gets the same scrambled word and may guess as often
 * as they like (BUG-20); a correct guess locks the player in with their solve order and time while
 * the others keep going. The round ends when every connected player has solved it (or used up their
 * guesses) or when time is up. See {@link Scoring} for the points.
 *
 * <p>Hints are personal and opt-in: the {@value #HINT} action reveals one more letter in its place
 * (the first letter, then one more) and costs that player 25 % of the round's points.
 */
final class WordScrambleLogic implements GameLogic {

    /** Action: guess the word. */
    static final String GUESS = "guess";

    /** Action: reveal one more letter. */
    static final String HINT = "hint";

    /** Hints per player and round. */
    static final int MAX_HINTS = 2;

    /** Upper limit of wrong guesses per player and round (anti brute force). */
    static final int MISS_CAP = 40;

    private final WordLength wordLength;
    private final boolean hintsEnabled;
    private final Set<String> usedWords = new HashSet<>();
    private final Map<String, PlayerRound> progress = new HashMap<>();
    private final Highlights highlights = new Highlights();
    private String word = "";
    private String scramble = "";
    private List<Integer> hintPositions = List.of();
    private int solvedCount;
    private boolean revealed;

    /** Payload of the {@value #GUESS} action. */
    record Guess(
            @NotNull(message = "is missing") @Size(max = 40, message = "is too long") String text) {}

    WordScrambleLogic(WordLength wordLength, boolean hintsEnabled) {
        this.wordLength = wordLength;
        this.hintsEnabled = hintsEnabled;
    }

    /**
     * Wrong guesses allowed per round: at most {@value #MISS_CAP} and never more than a quarter of
     * the possible letter orders, so short words can't be brute-forced (4 letters: 6 misses).
     */
    static int maxMisses(int length) {
        long orders = 1;
        for (int i = 2; i <= length; i++) {
            orders *= i;
        }
        return (int) Math.min(MISS_CAP, orders / 4);
    }

    @Override
    public void startTurn(GameContext ctx) {
        // Reset everything first: no view of the new round may carry the old answer (BUG-08).
        revealed = false;
        progress.clear();
        solvedCount = 0;
        word = WordBank.pick(wordLength.next(ctx.random()), ctx.random(), usedWords);
        usedWords.add(word);
        scramble = Scrambler.scramble(word, ctx.random());
        hintPositions = hintPositions(word.length(), ctx.random());
    }

    /** Where hints reveal letters: the first letter, then a random other position. */
    private static List<Integer> hintPositions(int length, RandomGenerator random) {
        return List.of(0, 1 + random.nextInt(length - 1));
    }

    @Override
    public void onAction(GameContext ctx, PlayerInfo player, Action action) {
        switch (action.type()) {
            case GUESS -> guess(ctx, mine(player), action.payload(Guess.class).text());
            case HINT -> hint(mine(player));
            default -> throw new InvalidActionException("Unknown action " + action.type());
        }
    }

    private PlayerRound mine(PlayerInfo player) {
        return progress.computeIfAbsent(player.id(), id -> new PlayerRound());
    }

    /** Checks and records a guess; wrong guesses are accepted actions (they change the view). */
    private void guess(GameContext ctx, PlayerRound mine, String raw) {
        requireGuessing(mine);
        String guess = GuessText.normalize(raw);
        if (guess.isEmpty()) {
            throw new InvalidActionException("Type a word first");
        }
        if (guess.length() != word.length()) {
            throw new InvalidActionException("The word has " + word.length() + " letters");
        }
        if (!WordBank.signature(guess).equals(WordBank.signature(word))) {
            throw new InvalidActionException("Use exactly the letters on the tiles");
        }
        if (guess.equals(word)) {
            solvedCount++;
            mine.solve(solvedCount, ctx.now());
            return;
        }
        if (mine.alreadyTried(guess)) {
            throw new InvalidActionException("You already tried " + guess);
        }
        mine.miss(guess);
    }

    private void hint(PlayerRound mine) {
        if (!hintsEnabled) {
            throw new InvalidActionException("Hints are switched off in this game");
        }
        requireGuessing(mine);
        if (mine.hintsUsed() >= MAX_HINTS) {
            throw new InvalidActionException("No hints left this round");
        }
        mine.takeHint();
    }

    private void requireGuessing(PlayerRound mine) {
        if (mine.solved()) {
            throw new InvalidActionException("You already solved it — wait for the others");
        }
        if (mine.misses() >= maxMisses(word.length())) {
            throw new InvalidActionException("No guesses left this round");
        }
    }

    @Override
    public boolean isTurnComplete(GameContext ctx) {
        List<PlayerInfo> active = ctx.activePlayers();
        return !active.isEmpty() && active.stream().allMatch(p -> isDone(progress.get(p.id())));
    }

    private boolean isDone(PlayerRound state) {
        return state != null && (state.solved() || state.misses() >= maxMisses(word.length()));
    }

    @Override
    public TurnOutcome endTurn(GameContext ctx) {
        revealed = true;
        TurnOutcome.Builder outcome = TurnOutcome.builder();
        PlayerInfo fastest = null;
        PlayerRound fastestState = null;
        for (PlayerInfo player : ctx.players()) {
            PlayerRound state = progress.get(player.id());
            if (state == null || !state.solved()) {
                continue;
            }
            outcome.add(
                    player.id(),
                    Scoring.points(
                            state.rank(),
                            state.solvedAt(),
                            ctx.turnStartedAt(),
                            ctx.deadline(),
                            state.hintsUsed()));
            long millis = state.solvedAt() - ctx.turnStartedAt();
            highlights.add(
                    new Highlights.Solve(player.id(), word, state.rank(), millis, state.misses()));
            if (fastestState == null || state.rank() < fastestState.rank()) {
                fastest = player;
                fastestState = state;
            }
        }
        String headline =
                fastest == null
                        ? "The word was " + word + " — nobody cracked it"
                        : "The word was "
                                + word
                                + " — "
                                + fastest.name()
                                + " was fastest ("
                                + Highlights.seconds(fastestState.solvedAt() - ctx.turnStartedAt())
                                + ")";
        return outcome.headline(headline).build();
    }

    @Override
    public Object view(GameContext ctx, Viewer viewer) {
        List<PlayerStatus> players = new ArrayList<>();
        for (PlayerInfo player : ctx.players()) {
            players.add(status(ctx, player.id(), progress.get(player.id())));
        }
        return new WordScrambleView(
                scramble,
                word.length(),
                revealed,
                revealed ? word : null,
                hintsEnabled ? MAX_HINTS : 0,
                maxMisses(word.length()),
                viewer.isSpectator() ? null : myStatus(ctx, progress.get(viewer.playerId())),
                players);
    }

    /** What everybody may know about a player: solved or not, rank and time — never guesses. */
    private PlayerStatus status(GameContext ctx, String playerId, PlayerRound state) {
        boolean solved = state != null && state.solved();
        return new PlayerStatus(
                playerId,
                solved,
                solved ? state.rank() : null,
                solved ? state.solvedAt() - ctx.turnStartedAt() : null,
                revealed ? (state == null ? 0 : state.hintsUsed()) : null,
                revealed ? (state == null ? 0 : state.misses()) : null);
    }

    /** The viewer's own progress, including their misses and the letters their hints revealed. */
    private MyStatus myStatus(GameContext ctx, PlayerRound state) {
        if (state == null) {
            return new MyStatus(false, null, null, 0, List.of(), 0, List.of());
        }
        List<HintLetter> hints = new ArrayList<>();
        for (int i = 0; i < state.hintsUsed(); i++) {
            int position = hintPositions.get(i);
            hints.add(new HintLetter(position, String.valueOf(word.charAt(position))));
        }
        boolean solved = state.solved();
        return new MyStatus(
                solved,
                solved ? state.rank() : null,
                solved ? state.solvedAt() - ctx.turnStartedAt() : null,
                state.misses(),
                state.recentMisses(),
                state.hintsUsed(),
                hints);
    }

    @Override
    public List<Award> awards(GameContext ctx) {
        return highlights.awards(
                ctx.players().stream().map(PlayerInfo::id).collect(Collectors.toSet()));
    }

    @Override
    public Object debugState(GameContext ctx) {
        return Map.of("word", word, "scramble", scramble);
    }
}
