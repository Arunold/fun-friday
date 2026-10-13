package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.BusPhysics.Pedals;
import com.example.funfridaygame.games.backseatdrivers.Simulation.Taps;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The players' inputs during a level: which hold controls everyone presses right now, plus the taps
 * since the last tick. Only inputs from the control's current holder reach this class (the logic
 * checks that); a shuffle releases everything.
 *
 * <p>A control acts when <em>all</em> its holders press it — with one holder that is simply "when
 * pressed", with power steering both players of a side must press together.
 */
final class InputBook {

    /** A tap lights up its control on everyone's roster this long. */
    static final long TAP_FLASH_MS = 300;

    private final Map<String, Set<Control>> held = new HashMap<>();
    private final Map<String, Map<Control, Long>> lastTap = new HashMap<>();
    private final List<String> honkers = new ArrayList<>();
    private boolean wipe;
    private int lightToggles;

    /**
     * Records a key or button change of a control the player holds.
     *
     * @param playerId who pressed
     * @param control the control
     * @param pressed key down ({@code true}) or up
     * @param now current time (epoch ms)
     */
    void press(String playerId, Control control, boolean pressed, long now) {
        if (control.isHold()) {
            Set<Control> mine = held.computeIfAbsent(playerId, id -> EnumSet.noneOf(Control.class));
            if (pressed) {
                mine.add(control);
            } else {
                mine.remove(control);
            }
            return;
        }
        if (!control.isTap() || !pressed) {
            return;
        }
        lastTap.computeIfAbsent(playerId, id -> new EnumMap<>(Control.class)).put(control, now);
        switch (control) {
            case HORN -> honkers.add(playerId);
            case WIPERS -> wipe = true;
            case HEADLIGHTS -> lightToggles++;
            default -> {
                // only taps get here
            }
        }
    }

    /** Releases everything a player holds (they left, lost the connection or changed seats). */
    void release(String playerId) {
        held.remove(playerId);
    }

    /** Releases every control (SEAT SHUFFLE). */
    void releaseAll() {
        held.clear();
    }

    /** Whether the player presses a hold control right now. */
    boolean pressing(String playerId, Control control) {
        return held.getOrDefault(playerId, Set.of()).contains(control);
    }

    /** The pedals and steering that act right now, given who holds what. */
    Pedals pedals(Seating seating) {
        boolean left = acting(seating, Control.STEER_LEFT);
        boolean right = acting(seating, Control.STEER_RIGHT);
        int steer = left == right ? 0 : left ? 1 : -1;
        return new Pedals(acting(seating, Control.GAS), acting(seating, Control.BRAKE), steer);
    }

    private boolean acting(Seating seating, Control control) {
        List<String> holders = seating.holders(control);
        return !holders.isEmpty() && holders.stream().allMatch(p -> pressing(p, control));
    }

    /**
     * The taps since the last call.
     *
     * @param taps what the taps do
     * @param honkers who honked (for the awards)
     */
    record TapBatch(Taps taps, List<String> honkers) {

        TapBatch {
            honkers = List.copyOf(honkers);
        }
    }

    /** The taps since the last call; clears them. */
    TapBatch takeTaps() {
        TapBatch batch =
                new TapBatch(new Taps(!honkers.isEmpty(), wipe, lightToggles % 2 == 1), honkers);
        honkers.clear();
        wipe = false;
        lightToggles = 0;
        return batch;
    }

    /**
     * The controls a player is pressing as frame bits: held controls plus recent taps.
     *
     * @param playerId the player
     * @param now current time (epoch ms)
     */
    int pressedMask(String playerId, long now) {
        int mask = 0;
        for (Control control : held.getOrDefault(playerId, Set.of())) {
            mask |= control.bit();
        }
        for (Map.Entry<Control, Long> tap : lastTap.getOrDefault(playerId, Map.of()).entrySet()) {
            if (now - tap.getValue() < TAP_FLASH_MS) {
                mask |= tap.getKey().bit();
            }
        }
        return mask;
    }
}
