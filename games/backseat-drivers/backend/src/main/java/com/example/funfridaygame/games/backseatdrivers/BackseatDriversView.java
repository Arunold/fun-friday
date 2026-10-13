package com.example.funfridaygame.games.backseatdrivers;

import java.util.List;
import java.util.Map;

/**
 * What a browser receives for Backseat Drivers. {@code null} fields are omitted from the JSON.
 *
 * <p>No cheating: only the navigator's view contains the whole map of the current level; drivers
 * and spectators get a {@linkplain AreaView window} around the bus. Everyone sees the full map with
 * the bus's trail once the level is over.
 *
 * @param mode {@code LEVEL} while a level runs (and during its reveal), {@code VOTE} for the MVP
 *     vote
 * @param variation {@code classic}, {@code rush-hour} or {@code silent-navigator}
 * @param shuffleSeconds time between SEAT SHUFFLEs
 * @param levels the finished levels so far (the team's history)
 * @param level the current level ({@code LEVEL} mode only)
 * @param vote the MVP vote ({@code VOTE} mode only)
 */
public record BackseatDriversView(
        String mode,
        String variation,
        int shuffleSeconds,
        List<LevelResult> levels,
        LevelView level,
        VoteView vote) {

    /**
     * The result of a finished level.
     *
     * @param number 1 to 3
     * @param name e.g. "Campus Loop"
     * @param status {@code PARKED}, {@code CRASHED} (the CEO's car) or {@code TIME_UP}
     * @param secondsLeft seconds on the clock when parking (0 otherwise)
     * @param coffees coffee cups collected
     * @param cones cones hit
     * @param score the level score every player got
     */
    public record LevelResult(
            int number,
            String name,
            String status,
            int secondsLeft,
            int coffees,
            int cones,
            int score) {}

    /**
     * The level being driven.
     *
     * @param number 1 to 3
     * @param name e.g. "Downtown"
     * @param status {@code DRIVING}, then {@code PARKED}, {@code CRASHED} or {@code TIME_UP}
     * @param rolesVersion increases with every (re)assignment of the seats
     * @param seats who holds what, navigator first; {@link FrameView#p()} follows this order
     * @param myControls the viewer's controls (empty without a seat, {@code null} for spectators)
     * @param navigator whether the viewer is the navigator
     * @param nextShuffleAt when the next SEAT SHUFFLE happens (epoch ms)
     * @param shuffleWarning the siren: a shuffle happens within two seconds
     * @param frame the latest frame
     * @param window the area around the bus (drivers and spectators while driving)
     * @param map the whole map (the navigator while driving; everyone once the level is over)
     * @param trail the bus's path {@code [x·100, y·100]} (once the level is over)
     * @param signal the latest silent-navigator signal
     * @param result the level's result (once it is over)
     */
    public record LevelView(
            int number,
            String name,
            String status,
            int rolesVersion,
            List<SeatView> seats,
            List<String> myControls,
            boolean navigator,
            long nextShuffleAt,
            boolean shuffleWarning,
            FrameView frame,
            AreaView window,
            AreaView map,
            List<int[]> trail,
            SignalView signal,
            LevelResult result) {}

    /**
     * One seat.
     *
     * @param playerId who sits there
     * @param controls {@code NAVIGATOR}, {@code STEER_LEFT}, {@code STEER_RIGHT}, {@code GAS},
     *     {@code BRAKE}, {@code HORN}, {@code WIPERS}, {@code HEADLIGHTS}
     */
    public record SeatView(String playerId, List<String> controls) {}

    /**
     * One simulation tick, streamed to everyone as event {@code frame} (compact on purpose).
     *
     * @param l level number (ticks restart every level)
     * @param t tick number
     * @param b bus {@code [x·1000, y·1000, heading·1000 (rad), speed·1000 (tiles/s)]}
     * @param p per seat (order of {@link LevelView#seats()}): bits of the pressed controls
     * @param e effect bits (blur 1, dark 2, honk 4, lights 8, bump 16, scrape 32, wipe 64, rain
     *     128, tunnel 256, geese 512, off-road 1024)
     * @param c cones hit
     * @param k coffee cups collected
     * @param g bit {@code i} set = flock {@code i} of geese is crossing
     * @param r roles version the pressed bits refer to
     */
    public record FrameView(int l, long t, int[] b, int[] p, int e, int c, int k, int g, int r) {}

    /**
     * A rectangle of the map with the hazards on it: the whole map for the navigator, or the
     * drivers' window around the bus (streamed as event {@code window}), where cells farther than
     * the view radius are {@code ?}.
     *
     * @param l level number
     * @param v version (the window's increases whenever it changes; 0 for the whole map)
     * @param x0 column of the first cell
     * @param y0 row of the first cell
     * @param w columns
     * @param h rows
     * @param rows ground characters per row (see the README legend), {@code ?} = unknown
     * @param cones cones still on the road {@code [x, y]}
     * @param coffees coffee cups still on the road {@code [x, y]}
     * @param geese flocks that are still around
     */
    public record AreaView(
            int l,
            int v,
            int x0,
            int y0,
            int w,
            int h,
            List<String> rows,
            List<int[]> cones,
            List<int[]> coffees,
            List<GooseView> geese) {}

    /**
     * A flock of geese.
     *
     * @param i flock index (bit in {@link FrameView#g()})
     * @param cells the road cells it blocks while crossing {@code [x, y]}
     */
    public record GooseView(int i, List<int[]> cells) {}

    /**
     * A silent-navigator signal, streamed as event {@code signal}.
     *
     * @param id increases per signal
     * @param kind {@code LEFT}, {@code RIGHT}, {@code STRAIGHT}, {@code STOP}, {@code HONK}, {@code
     *     WIPE} or {@code LIGHTS}
     * @param at when it was given (epoch ms)
     */
    public record SignalView(int id, String kind, long at) {}

    /**
     * The MVP vote after the last level.
     *
     * @param revealed whether the vote is over
     * @param candidates everyone in the game (nobody can vote for themselves)
     * @param votesCast how many votes are in (not who for)
     * @param voters how many connected players can vote
     * @param voted who has voted already
     * @param myVote whom the viewer voted for
     * @param tally votes per player (once revealed)
     * @param winners the most-voted players (once revealed)
     */
    public record VoteView(
            boolean revealed,
            List<String> candidates,
            int votesCast,
            int voters,
            List<String> voted,
            String myVote,
            Map<String, Integer> tally,
            List<String> winners) {}
}
