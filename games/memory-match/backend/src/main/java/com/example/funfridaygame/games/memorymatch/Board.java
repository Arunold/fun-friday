package com.example.funfridaygame.games.memorymatch;

import com.example.funfridaygame.games.memorymatch.MemoryMatchView.CardView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The cards of one round. Faces are the round's secret: {@link #cardViews(boolean)} only exposes
 * the emoji of cards that are face up or matched (or of every card once the round is revealed).
 */
final class Board {

    /** What a card shows right now. */
    enum CardState {
        /** Face down. */
        HIDDEN,
        /** Face up during a move (or, after the reveal, a pair nobody found). */
        UP,
        /** Part of a found pair; stays face up in the finder's colour. */
        MATCHED
    }

    private final BoardSize size;
    private final List<String> faces;
    private final CardState[] states;
    private final String[] matchedBy;
    private final boolean[] seen;

    private Board(BoardSize size, List<String> faces) {
        this.size = size;
        this.faces = List.copyOf(faces);
        this.states = new CardState[faces.size()];
        this.matchedBy = new String[faces.size()];
        this.seen = new boolean[faces.size()];
        Arrays.fill(states, CardState.HIDDEN);
    }

    /** Deals a shuffled board: {@code size.pairs()} different faces of the theme, each twice. */
    static Board deal(BoardSize size, CardTheme theme, RandomGenerator random) {
        List<String> faces = new ArrayList<>(size.cards());
        for (String face : theme.pick(size.pairs(), random)) {
            faces.add(face);
            faces.add(face);
        }
        Collections.shuffle(faces, random);
        return new Board(size, faces);
    }

    /** The layout of this board. */
    BoardSize size() {
        return size;
    }

    /** Whether {@code index} is a card of this board. */
    boolean contains(int index) {
        return index >= 0 && index < faces.size();
    }

    CardState state(int index) {
        return states[index];
    }

    /**
     * Turns a hidden card face up.
     *
     * @return whether this card had already been face up earlier in the round
     */
    boolean flip(int index) {
        if (states[index] != CardState.HIDDEN) {
            throw new IllegalStateException("Card " + index + " is not face down");
        }
        states[index] = CardState.UP;
        boolean seenBefore = seen[index];
        seen[index] = true;
        return seenBefore;
    }

    /** Turns a face-up card back down (matched cards stay up). */
    void hide(int index) {
        if (contains(index) && states[index] == CardState.UP) {
            states[index] = CardState.HIDDEN;
        }
    }

    /** Whether two cards show the same face. */
    boolean sameFace(int first, int second) {
        return faces.get(first).equals(faces.get(second));
    }

    /** Marks two face-up cards as a pair found by {@code playerId}. */
    void match(int first, int second, String playerId) {
        for (int index : new int[] {first, second}) {
            states[index] = CardState.MATCHED;
            matchedBy[index] = playerId;
        }
    }

    /** Pairs nobody has found yet. */
    int pairsLeft() {
        int matched = 0;
        for (CardState state : states) {
            if (state == CardState.MATCHED) {
                matched++;
            }
        }
        return (faces.size() - matched) / 2;
    }

    /** Whether every pair has been found. */
    boolean cleared() {
        return pairsLeft() == 0;
    }

    /**
     * The cards as browsers see them.
     *
     * @param revealAll {@code true} after the round: unfound cards are shown face up
     */
    List<CardView> cardViews(boolean revealAll) {
        List<CardView> cards = new ArrayList<>(faces.size());
        for (int i = 0; i < faces.size(); i++) {
            CardState state =
                    revealAll && states[i] != CardState.MATCHED ? CardState.UP : states[i];
            String emoji = state == CardState.HIDDEN ? null : faces.get(i);
            cards.add(new CardView(i, state.name(), emoji, matchedBy[i]));
        }
        return cards;
    }

    /** Every face in board order — secret, for {@code debugState()} only. */
    List<String> faces() {
        return faces;
    }

    /** Index pairs of the cards nobody has found yet — secret, for {@code debugState()} only. */
    List<List<Integer>> unmatchedPairs() {
        Map<String, List<Integer>> byFace = new LinkedHashMap<>();
        for (int i = 0; i < faces.size(); i++) {
            if (states[i] != CardState.MATCHED) {
                byFace.computeIfAbsent(faces.get(i), face -> new ArrayList<>()).add(i);
            }
        }
        return byFace.values().stream().map(List::copyOf).toList();
    }
}
