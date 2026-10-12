package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.List;
import java.util.random.RandomGenerator;

/** State of one turn: the drawer, the word choice, the drawing, the guesses and how it ended. */
final class Turn {

    /** The two parts of a turn. */
    enum Phase {
        /** The drawer picks one of three words (10 s). */
        CHOOSING,
        /** The drawer draws, everybody else guesses (the round timer). */
        DRAWING
    }

    /** Why a turn ended; shown on the reveal. */
    enum End {
        /** Every connected guesser got the word. */
        ALL_GUESSED,
        /** The timer ran out. */
        TIME_UP,
        /** The drawer left the game; nobody scores. */
        DRAWER_LEFT,
        /** The drawer lost the connection; nobody scores. */
        DRAWER_OFFLINE,
        /** Everybody else left the game. */
        NO_GUESSERS;

        /** Whether the turn is void (no points for anybody). */
        boolean voids() {
            return this == DRAWER_LEFT || this == DRAWER_OFFLINE;
        }
    }

    private final int key;
    private final PlayerInfo drawer;
    private final List<WordPicker.Choice> choices;
    private final Drawing drawing;
    private final GuessBook guesses = new GuessBook();
    private Phase phase = Phase.CHOOSING;
    private WordPicker.Choice word;
    private HintMask mask;
    private long drawStartedAt;
    private End end;
    private int drawerPoints;

    /**
     * A new turn in the choosing phase.
     *
     * @param key sequential number of the turn in the game
     * @param drawer who draws
     * @param choices the words offered to the drawer
     */
    Turn(int key, PlayerInfo drawer, List<WordPicker.Choice> choices) {
        this.key = key;
        this.drawer = drawer;
        this.choices = List.copyOf(choices);
        this.drawing = new Drawing(key);
    }

    /** Moves to the drawing phase with the chosen word. */
    void startDrawing(WordPicker.Choice choice, long now, RandomGenerator random) {
        this.word = choice;
        this.mask = new HintMask(choice.word(), random);
        this.phase = Phase.DRAWING;
        this.drawStartedAt = now;
    }

    /** Ends the turn. */
    void finish(End reason, int pointsForDrawer) {
        this.end = reason;
        this.drawerPoints = pointsForDrawer;
    }

    /** Sequential number of the turn in the game. */
    int key() {
        return key;
    }

    /** Who draws. */
    PlayerInfo drawer() {
        return drawer;
    }

    /** Whether {@code playerId} draws this turn. */
    boolean isDrawer(String playerId) {
        return drawer.id().equals(playerId);
    }

    /** The words offered to the drawer. */
    List<WordPicker.Choice> choices() {
        return choices;
    }

    /** The drawing log. */
    Drawing drawing() {
        return drawing;
    }

    /** The guesses. */
    GuessBook guesses() {
        return guesses;
    }

    /** Current phase. */
    Phase phase() {
        return phase;
    }

    /** The chosen word, {@code null} while choosing. */
    String word() {
        return word == null ? null : word.word();
    }

    /** The masked word, {@code null} while choosing. */
    HintMask mask() {
        return mask;
    }

    /** When the drawing phase started (epoch ms). */
    long drawStartedAt() {
        return drawStartedAt;
    }

    /** Whether the turn is over. */
    boolean revealed() {
        return end != null;
    }

    /** Why the turn ended, {@code null} while it runs. */
    End end() {
        return end;
    }

    /** Points the drawer earned (once revealed). */
    int drawerPoints() {
        return drawerPoints;
    }
}
