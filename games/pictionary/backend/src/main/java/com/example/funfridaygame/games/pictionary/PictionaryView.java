package com.example.funfridaygame.games.pictionary;

import java.util.List;

/**
 * What a browser receives for Pictionary. The secret word only reaches the drawer, players who have
 * already guessed it, and everybody once the turn is revealed; everyone else gets the masked word.
 * The drawing log is the same for every viewer (spectators included), so every snapshot re-syncs
 * all canvases.
 *
 * @param turn sequential number of the turn in this game (repeated on every drawing event)
 * @param phase {@code CHOOSING} (the drawer picks a word) or {@code DRAWING}
 * @param revealed whether the turn is over
 * @param drawerId the drawing player
 * @param drawerName their name (kept when they leave)
 * @param choices the three words to choose from (drawer only, while choosing)
 * @param word the secret word (drawer, players who guessed it, everybody once revealed)
 * @param mask the word with hidden letters shown as {@code _} (while drawing)
 * @param guessed whether the viewer guessed the word ({@code null} for the drawer and spectators)
 * @param guessesLeft guesses the viewer may still make this turn (guessers while drawing)
 * @param feed the latest guesses; near misses only appear for their author
 * @param correct correct guesses in the order they were made
 * @param guessers how many players guess this turn
 * @param drawing the authoritative drawing log
 * @param endReason why the turn ended (revealed only), see {@link Turn.End}
 * @param drawerPoints points the drawer earned (revealed only)
 */
public record PictionaryView(
        int turn,
        String phase,
        boolean revealed,
        String drawerId,
        String drawerName,
        List<ChoiceView> choices,
        String word,
        String mask,
        Boolean guessed,
        Integer guessesLeft,
        List<FeedEntryView> feed,
        List<CorrectView> correct,
        int guessers,
        DrawingView drawing,
        String endReason,
        Integer drawerPoints) {

    /**
     * One of the words offered to the drawer.
     *
     * @param word the word
     * @param level EASY, MEDIUM or HARD
     */
    public record ChoiceView(String word, String level) {}

    /**
     * One line of the guess feed.
     *
     * @param id increasing number within the turn
     * @param playerId who guessed
     * @param name their name at the time
     * @param kind GUESS (wrong, public), CLOSE (near miss, only shown to its author) or CORRECT
     * @param text the guess as typed ({@code null} for correct guesses, which never show the word)
     */
    public record FeedEntryView(int id, String playerId, String name, String kind, String text) {}

    /**
     * A correct guess.
     *
     * @param playerId who guessed
     * @param name their name at the time
     * @param ms milliseconds from the start of the drawing phase to the guess
     * @param points points awarded for it (revealed only; 0 when the turn was voided)
     */
    public record CorrectView(String playerId, String name, long ms, Integer points) {}

    /**
     * The drawing of a turn on the logical {@value Drawing#WIDTH}×{@value Drawing#HEIGHT} canvas.
     *
     * @param turn the turn it belongs to
     * @param version increases with every change; drawing events carry the version they produce
     * @param undo how many steps can be undone
     * @param redo how many steps can be redone
     * @param ops visible operations, oldest first
     */
    public record DrawingView(int turn, int version, int undo, int redo, List<OpView> ops) {}

    /**
     * A drawing operation.
     *
     * @param id client-chosen id, unique within the turn
     * @param tool {@code pen}, {@code eraser} or {@code fill}
     * @param color CSS hex colour (the eraser paints the paper colour)
     * @param size brush diameter in logical pixels (0 for fills)
     * @param points {@code x1, y1, x2, y2, …} in logical pixels; a fill has a single point
     */
    public record OpView(int id, String tool, String color, int size, int[] points) {}

    /**
     * Stream event {@code stroke}: a batch of points that starts or continues stroke {@code op.id}.
     *
     * @param turn the turn it belongs to
     * @param version the drawing version after the batch
     * @param undo steps that can be undone afterwards
     * @param redo steps that can be redone afterwards
     * @param op the stroke with only the new points
     */
    public record StrokeEvent(int turn, int version, int undo, int redo, OpView op) {}

    /**
     * Stream event {@code edit}: fill, undo, redo or clear. Applied in this order: {@code reset}
     * empties the canvas, {@code remove} deletes one operation, {@code add} appends operations.
     *
     * @param turn the turn it belongs to
     * @param version the drawing version after the change
     * @param undo steps that can be undone afterwards
     * @param redo steps that can be redone afterwards
     * @param reset whether to empty the canvas first
     * @param remove id of an operation to remove
     * @param add operations to append
     */
    public record EditEvent(
            int turn,
            int version,
            int undo,
            int redo,
            Boolean reset,
            Integer remove,
            List<OpView> add) {}

    /**
     * Private event {@code close}: the viewer's guess was nearly right.
     *
     * @param text the guess
     */
    public record CloseEvent(String text) {}
}
