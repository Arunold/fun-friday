package com.example.funfridaygame.games.pictionary;

import com.example.funfridaygame.games.pictionary.PictionaryView.DrawingView;
import com.example.funfridaygame.games.pictionary.PictionaryView.EditEvent;
import com.example.funfridaygame.games.pictionary.PictionaryView.OpView;
import com.example.funfridaygame.games.pictionary.PictionaryView.StrokeEvent;
import com.example.funfridaygame.sdk.InvalidActionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The authoritative drawing of one turn: an ordered log of strokes and fills with undo, redo and
 * clear (BUG-18). Every change bumps {@link #version()} and produces one event for the stream;
 * clients apply events in version order and re-sync from {@link #view()} with every snapshot, so
 * all canvases render exactly the same log.
 *
 * <p>Internally the log is a history of entries — operations and clear markers — plus a redo stack.
 * The visible picture is everything after the last clear marker, so undoing a clear simply brings
 * back what it removed.
 */
final class Drawing {

    /** Width of the logical canvas; every client scales it to fit (4:3). */
    static final int WIDTH = 1000;

    /** Height of the logical canvas. */
    static final int HEIGHT = 750;

    /** Largest brush diameter in logical pixels (the eraser is the widest). */
    static final int MAX_SIZE = 80;

    /** Points per stroke batch; clients send a batch about every 80 ms. */
    static final int MAX_BATCH_POINTS = 256;

    /** Points per stroke; clients start a new stroke before reaching it. */
    static final int MAX_STROKE_POINTS = 2_000;

    /** Points in the visible picture, which keeps snapshots compact (undo or clear frees them). */
    static final int MAX_PICTURE_POINTS = 25_000;

    /** Points drawn per turn including undone and cleared ones, which bounds memory. */
    static final int MAX_TURN_POINTS = 100_000;

    /** Strokes and fills per turn. */
    static final int MAX_OPS = 2_000;

    /** Drawing tools; the JSON key is the lower-case name. */
    enum Tool {
        PEN,
        ERASER,
        FILL;

        /** The key used in payloads and views. */
        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Parses a payload key. */
        static Tool fromKey(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }
    }

    /** An entry of the history: an operation or a clear marker. */
    private sealed interface Entry permits Op, Clear {}

    /** A stroke (it grows while being drawn) or a fill (a single point). */
    private static final class Op implements Entry {
        private final int id;
        private final Tool tool;
        private final String color;
        private final int size;
        private int[] points;
        private int length;
        private OpView view;

        private Op(int id, Tool tool, String color, int size, int[] first) {
            this.id = id;
            this.tool = tool;
            this.color = color;
            this.size = size;
            this.points = Arrays.copyOf(first, Math.max(32, first.length));
            this.length = first.length;
        }

        private void append(int[] more) {
            if (length + more.length > points.length) {
                points = Arrays.copyOf(points, Math.max(points.length * 2, length + more.length));
            }
            System.arraycopy(more, 0, points, length, more.length);
            length += more.length;
        }

        private int pointCount() {
            return length / 2;
        }

        /** The operation as clients see it, cached until it grows. */
        private OpView view() {
            if (view == null || view.points().length != length) {
                view = new OpView(id, tool.key(), color, size, Arrays.copyOf(points, length));
            }
            return view;
        }
    }

    /**
     * Empties the canvas.
     *
     * @param before what was visible, restored when the clear is undone
     */
    private record Clear(List<Op> before) implements Entry {}

    private final int turn;
    private final List<Entry> history = new ArrayList<>();
    private final Deque<Entry> redo = new ArrayDeque<>();
    private final List<Op> visible = new ArrayList<>();
    private final Set<Integer> ids = new HashSet<>();
    private int version;
    private int totalPoints;
    private DrawingView view;

    /** An empty drawing for the given turn number. */
    Drawing(int turn) {
        this.turn = turn;
    }

    /**
     * Adds a batch of points: continues the newest stroke when {@code id} matches it, otherwise
     * starts a new stroke.
     *
     * @return the event to relay, or empty for a late batch of a stroke that is no longer the
     *     newest (e.g. it was undone meanwhile)
     * @throws InvalidActionException when the batch is malformed or a limit is reached
     */
    Optional<StrokeEvent> stroke(int id, Tool tool, String color, int size, int[] batch) {
        checkPoints(batch);
        int count = batch.length / 2;
        Op newest = newestOp();
        if (newest != null && newest.id == id && newest.tool != Tool.FILL) {
            if (newest.pointCount() + count > MAX_STROKE_POINTS) {
                throw new InvalidActionException("That line is very long — lift the pen and go on");
            }
            reserve(count);
            newest.append(batch);
            version++;
            return Optional.of(strokeEvent(newest, batch));
        }
        if (ids.contains(id)) {
            return Optional.empty();
        }
        Op op = new Op(id, tool, normalizeColor(color), size, batch);
        add(op, count);
        return Optional.of(strokeEvent(op, batch));
    }

    /**
     * Adds a bucket fill at a point; clients flood-fill in log order on the fixed-size canvas, so
     * the result is the same everywhere.
     *
     * @return the event to relay, or empty if the id was already used (a repeated message)
     */
    Optional<EditEvent> fill(int id, String color, int x, int y) {
        if (ids.contains(id)) {
            return Optional.empty();
        }
        Op op = new Op(id, Tool.FILL, normalizeColor(color), 0, new int[] {x, y});
        add(op, 1);
        return Optional.of(edit(null, null, List.of(op.view())));
    }

    /**
     * Undoes the newest step (a stroke, a fill or a clear); empty when there is nothing to undo.
     */
    Optional<EditEvent> undo() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = history.removeLast();
        redo.push(entry);
        version++;
        if (entry instanceof Op op) {
            visible.removeLast();
            return Optional.of(edit(null, op.id, null));
        }
        visible.addAll(((Clear) entry).before());
        return Optional.of(edit(true, null, views()));
    }

    /** Redoes the most recently undone step; empty when there is nothing to redo. */
    Optional<EditEvent> redo() {
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = redo.pop();
        history.add(entry);
        version++;
        if (entry instanceof Op op) {
            visible.add(op);
            return Optional.of(edit(null, null, List.of(op.view())));
        }
        visible.clear();
        return Optional.of(edit(true, null, null));
    }

    /** Clears the canvas (undoable); empty when it is already blank. */
    Optional<EditEvent> clear() {
        if (visible.isEmpty()) {
            return Optional.empty();
        }
        history.add(new Clear(List.copyOf(visible)));
        visible.clear();
        redo.clear();
        version++;
        return Optional.of(edit(true, null, null));
    }

    /** The full visible log, cached per version (shared by every viewer's snapshot). */
    DrawingView view() {
        if (view == null || view.version() != version) {
            view = new DrawingView(turn, version, history.size(), redo.size(), views());
        }
        return view;
    }

    /** Increases with every change. */
    int version() {
        return version;
    }

    /** Number of visible operations. */
    int visibleOps() {
        return visible.size();
    }

    /** Number of points in the visible operations. */
    int visiblePoints() {
        return visible.stream().mapToInt(Op::pointCount).sum();
    }

    private Op newestOp() {
        return !history.isEmpty() && history.getLast() instanceof Op op ? op : null;
    }

    private void add(Op op, int points) {
        if (ids.size() >= MAX_OPS) {
            throw new InvalidActionException("That's a lot of drawing for one turn!");
        }
        reserve(points);
        ids.add(op.id);
        history.add(op);
        visible.add(op);
        redo.clear();
        version++;
    }

    private void reserve(int points) {
        if (visiblePoints() + points > MAX_PICTURE_POINTS) {
            throw new InvalidActionException("The canvas is full — undo or clear to keep drawing");
        }
        if (totalPoints + points > MAX_TURN_POINTS) {
            throw new InvalidActionException("That's a lot of drawing for one turn!");
        }
        totalPoints += points;
    }

    private List<OpView> views() {
        return visible.stream().map(Op::view).toList();
    }

    private StrokeEvent strokeEvent(Op op, int[] batch) {
        OpView chunk = new OpView(op.id, op.tool.key(), op.color, op.size, batch.clone());
        return new StrokeEvent(turn, version, history.size(), redo.size(), chunk);
    }

    private EditEvent edit(Boolean reset, Integer remove, List<OpView> add) {
        return new EditEvent(turn, version, history.size(), redo.size(), reset, remove, add);
    }

    private static void checkPoints(int[] batch) {
        if (batch.length < 2 || batch.length % 2 != 0 || batch.length > 2 * MAX_BATCH_POINTS) {
            throw new InvalidActionException("That stroke didn't come through, please draw again");
        }
        for (int i = 0; i < batch.length; i += 2) {
            if (batch[i] < 0 || batch[i] > WIDTH || batch[i + 1] < 0 || batch[i + 1] > HEIGHT) {
                throw new InvalidActionException("Keep your strokes on the canvas");
            }
        }
    }

    private static String normalizeColor(String color) {
        return color.toLowerCase(Locale.ROOT);
    }
}
