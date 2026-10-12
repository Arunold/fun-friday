package com.example.funfridaygame.games.pictionary;

import static com.example.funfridaygame.games.pictionary.TestGames.drawing;
import static com.example.funfridaygame.games.pictionary.TestGames.fill;
import static com.example.funfridaygame.games.pictionary.TestGames.fixed;
import static com.example.funfridaygame.games.pictionary.TestGames.ops;
import static com.example.funfridaygame.games.pictionary.TestGames.stroke;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.games.pictionary.PictionaryView.DrawingView;
import com.example.funfridaygame.games.pictionary.PictionaryView.EditEvent;
import com.example.funfridaygame.games.pictionary.PictionaryView.OpView;
import com.example.funfridaygame.games.pictionary.PictionaryView.StrokeEvent;
import com.example.funfridaygame.sdk.InvalidActionException;
import com.example.funfridaygame.testkit.GameHarness;
import com.example.funfridaygame.testkit.GameHarness.Event;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The authoritative drawing log: streaming, undo/redo/clear, limits and re-sync (BUG-18). */
class DrawingTest {

    private static GameHarness drawingTurn() {
        GameHarness game = fixed("alice", "bob", "carol");
        game.act("alice", "choose", Map.of("index", 0));
        return game;
    }

    @Test
    void strokesAreRelayedWithoutSnapshotsAndKeptInTheLog() {
        GameHarness game = drawingTurn();
        int snapshots = game.changes();

        game.act("alice", "stroke", stroke(1, 10, 10, 20, 20));
        game.act("alice", "stroke", stroke(1, 30, 30, 40, 40));

        assertThat(game.changes()).as("no snapshot per stroke").isEqualTo(snapshots);
        List<Event> events = game.streamed();
        assertThat(events).extracting(Event::type).containsExactly("stroke", "stroke");
        StrokeEvent second = (StrokeEvent) events.get(1).payload();
        assertThat(second.turn()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(second.undo()).isEqualTo(1);
        assertThat(second.op().id()).isEqualTo(1);
        assertThat(second.op().points()).containsExactly(30, 30, 40, 40);

        Map<String, Object> drawing = drawing(game.view("bob"));
        assertThat(drawing.get("version")).isEqualTo(2);
        assertThat(ops(game.view("bob")))
                .singleElement()
                .satisfies(
                        op -> {
                            assertThat(op.get("tool")).isEqualTo("pen");
                            assertThat(op.get("color")).isEqualTo("#222222");
                            assertThat(op.get("size")).isEqualTo(10);
                            assertThat(op.get("points"))
                                    .isEqualTo(List.of(10, 10, 20, 20, 30, 30, 40, 40));
                        });
    }

    @Test
    void undoRedoClearAndFillAreAppliedOnTheServer() {
        GameHarness game = drawingTurn();
        game.act("alice", "stroke", stroke(1, 10, 10, 20, 20))
                .act("alice", "stroke", stroke(2, 50, 50, 60, 60))
                .act("alice", "fill", fill(3, "#FF0000", 500, 400));
        assertThat(opIds(game)).containsExactly(1, 2, 3);
        assertThat(ops(game.view("bob")).getLast().get("color")).isEqualTo("#ff0000");

        game.act("alice", "undo", Map.of()).act("alice", "undo", Map.of());
        assertThat(opIds(game)).containsExactly(1);
        game.act("alice", "redo", Map.of());
        assertThat(opIds(game)).containsExactly(1, 2);
        game.act("alice", "clear", Map.of());
        assertThat(opIds(game)).isEmpty();
        game.act("alice", "undo", Map.of());
        assertThat(opIds(game)).as("a clear can be undone").containsExactly(1, 2);

        List<Event> events = game.streamed();
        assertThat(events)
                .extracting(Event::type)
                .containsExactly(
                        "stroke", "stroke", "edit", "edit", "edit", "edit", "edit", "edit");
        assertThat(events)
                .extracting(e -> version(e.payload()))
                .as("consecutive versions let clients spot gaps")
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        EditEvent undoFill = (EditEvent) events.get(3).payload();
        assertThat(undoFill.remove()).isEqualTo(3);
        EditEvent redo = (EditEvent) events.get(5).payload();
        assertThat(redo.add()).extracting(OpView::id).containsExactly(2);
        EditEvent clear = (EditEvent) events.get(6).payload();
        assertThat(clear.reset()).isTrue();
        EditEvent restore = (EditEvent) events.get(7).payload();
        assertThat(restore.reset()).isTrue();
        assertThat(restore.add()).extracting(OpView::id).containsExactly(1, 2);
        assertThat(drawing(game.view("carol")).get("redo")).as("only the clear").isEqualTo(1);
    }

    @Test
    void aNewStrokeDiscardsTheRedoStack() {
        GameHarness game = drawingTurn();
        game.act("alice", "stroke", stroke(1, 10, 10)).act("alice", "undo", Map.of());
        assertThat(drawing(game.view("bob")).get("redo")).isEqualTo(1);

        game.act("alice", "stroke", stroke(2, 20, 20));

        assertThat(drawing(game.view("bob")).get("redo")).isEqualTo(0);
        int version = game.streamed().size();
        game.act("alice", "redo", Map.of());
        assertThat(game.streamed()).as("nothing to redo: no event").hasSize(version);
    }

    @Test
    void lateBatchesOfAnUndoneStrokeAreIgnored() {
        GameHarness game = drawingTurn();
        game.act("alice", "stroke", stroke(1, 10, 10))
                .act("alice", "stroke", stroke(2, 20, 20))
                .act("alice", "undo", Map.of());
        int events = game.streamed().size();

        game.act("alice", "stroke", stroke(2, 30, 30));
        game.act("alice", "fill", fill(1, "#00ff00", 5, 5));

        assertThat(game.streamed()).hasSize(events);
        assertThat(opIds(game)).containsExactly(1);
    }

    @Test
    void malformedStrokesAreRejected() {
        GameHarness game = drawingTurn();

        assertThat(game.reject("alice", "stroke", stroke(1, 10, 10, 20)).getMessage())
                .contains("didn't come through");
        assertThat(game.reject("alice", "stroke", stroke(1, 10, 10, 2000, 20)).getMessage())
                .contains("on the canvas");
        assertThat(game.reject("alice", "stroke", stroke(1, -1, 10)).getMessage())
                .contains("canvas");
        assertThat(game.reject("alice", "stroke", stroke(0, 10, 10)).getMessage()).contains("id");
        int[] tooMany = IntStream.range(0, 2 * Drawing.MAX_BATCH_POINTS + 2).map(i -> 5).toArray();
        assertThat(game.reject("alice", "stroke", stroke(1, tooMany)).getMessage())
                .contains("points");
        Map<String, Object> red =
                Map.of(
                        "id",
                        1,
                        "tool",
                        "pen",
                        "color",
                        "red",
                        "size",
                        10,
                        "points",
                        new int[] {1, 1});
        assertThat(game.reject("alice", "stroke", red).getMessage()).contains("color");
        Map<String, Object> brush =
                Map.of(
                        "id",
                        1,
                        "tool",
                        "brush",
                        "color",
                        "#000000",
                        "size",
                        10,
                        "points",
                        new int[] {1, 1});
        assertThat(game.reject("alice", "stroke", brush).getMessage()).contains("tool");
        assertThat(game.reject("alice", "fill", fill(9, "#000000", 1000, 10)).getMessage())
                .contains("x");
        assertThat(game.streamed()).isEmpty();
    }

    @Test
    void aSyncRequestIsAnsweredPrivatelyWithTheWholeDrawing() {
        GameHarness game = drawingTurn();
        game.act("alice", "stroke", stroke(1, 10, 10, 20, 20));
        int snapshots = game.changes();

        game.act("bob", "sync", Map.of());

        assertThat(game.changes()).isEqualTo(snapshots);
        assertThat(game.privateEvents("bob"))
                .singleElement()
                .satisfies(
                        event -> {
                            assertThat(event.type()).isEqualTo("sync");
                            DrawingView view = (DrawingView) event.payload();
                            assertThat(view.version()).isEqualTo(1);
                            assertThat(view.ops()).extracting(OpView::id).containsExactly(1);
                        });
        assertThat(game.privateEvents("carol")).isEmpty();
    }

    @Test
    void strokesHaveALengthLimit() {
        Drawing drawing = new Drawing(1);
        int[] batch = IntStream.range(0, 2 * Drawing.MAX_BATCH_POINTS).map(i -> 7).toArray();
        int batches = Drawing.MAX_STROKE_POINTS / Drawing.MAX_BATCH_POINTS;
        for (int i = 0; i < batches; i++) {
            drawing.stroke(1, Drawing.Tool.PEN, "#000000", 4, batch);
        }

        assertThatThrownBy(() -> drawing.stroke(1, Drawing.Tool.PEN, "#000000", 4, batch))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("lift the pen");
        assertThat(drawing.stroke(2, Drawing.Tool.PEN, "#000000", 4, batch)).isPresent();
        assertThat(drawing.visiblePoints())
                .isEqualTo(batches * Drawing.MAX_BATCH_POINTS + Drawing.MAX_BATCH_POINTS);
    }

    @Test
    void thePictureHasAPointBudgetThatClearingFrees() {
        Drawing drawing = new Drawing(1);
        int[] batch = IntStream.range(0, 2 * Drawing.MAX_BATCH_POINTS).map(i -> 3).toArray();
        int fits = Drawing.MAX_PICTURE_POINTS / Drawing.MAX_BATCH_POINTS;
        for (int id = 1; id <= fits; id++) {
            drawing.stroke(id, Drawing.Tool.PEN, "#000000", 4, batch);
        }

        assertThatThrownBy(() -> drawing.stroke(fits + 1, Drawing.Tool.PEN, "#000000", 4, batch))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("canvas is full");
        drawing.clear();
        assertThat(drawing.stroke(fits + 1, Drawing.Tool.PEN, "#000000", 4, batch)).isPresent();
    }

    @Test
    void aTurnHasAPointBudgetThatClearingDoesNotFree() {
        Drawing drawing = new Drawing(1);
        int[] batch = IntStream.range(0, 2 * Drawing.MAX_BATCH_POINTS).map(i -> 3).toArray();
        int id = 1;
        for (int drawn = 0;
                drawn + Drawing.MAX_BATCH_POINTS <= Drawing.MAX_TURN_POINTS;
                drawn += Drawing.MAX_BATCH_POINTS) {
            drawing.stroke(id++, Drawing.Tool.PEN, "#000000", 4, batch);
            drawing.clear();
        }
        int next = id;

        assertThatThrownBy(() -> drawing.stroke(next, Drawing.Tool.PEN, "#000000", 4, batch))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("a lot of drawing");
    }

    @Test
    void theViewIsCachedPerVersion() {
        Drawing drawing = new Drawing(3);
        drawing.stroke(1, Drawing.Tool.ERASER, "#ABCDEF", 20, new int[] {1, 2});
        DrawingView first = drawing.view();

        assertThat(drawing.view()).isSameAs(first);
        assertThat(first.turn()).isEqualTo(3);
        assertThat(first.ops())
                .singleElement()
                .satisfies(
                        op -> {
                            assertThat(op.tool()).isEqualTo("eraser");
                            assertThat(op.color()).isEqualTo("#abcdef");
                        });
        drawing.stroke(1, Drawing.Tool.ERASER, "#abcdef", 20, new int[] {3, 4});
        assertThat(drawing.view()).isNotSameAs(first);
        assertThat(drawing.view().ops().getFirst().points()).containsExactly(1, 2, 3, 4);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Object> opIds(GameHarness game) {
        return ops(game.view("bob")).stream().map(op -> op.get("id")).toList();
    }

    private static int version(Object event) {
        return event instanceof StrokeEvent stroke
                ? stroke.version()
                : ((EditEvent) event).version();
    }
}
