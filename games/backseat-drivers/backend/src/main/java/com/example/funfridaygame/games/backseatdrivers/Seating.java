package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.Control.BRAKE;
import static com.example.funfridaygame.games.backseatdrivers.Control.GAS;
import static com.example.funfridaygame.games.backseatdrivers.Control.HEADLIGHTS;
import static com.example.funfridaygame.games.backseatdrivers.Control.HORN;
import static com.example.funfridaygame.games.backseatdrivers.Control.NAVIGATOR;
import static com.example.funfridaygame.games.backseatdrivers.Control.STEER_LEFT;
import static com.example.funfridaygame.games.backseatdrivers.Control.STEER_RIGHT;
import static com.example.funfridaygame.games.backseatdrivers.Control.WIPERS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Who holds which controls.
 *
 * <p>{@link #layout(int)} defines the seats for a number of players: one navigator (from two
 * players on) and the driving seats; utility controls without a seat of their own are added to
 * existing seats, spread evenly, so every hazard can always be handled. With 9–10 players each
 * steering side has two holders (power steering).
 *
 * <p>A {@linkplain Mode#SHUFFLE shuffle} deals the seats at random: the navigator must change and
 * everyone preferably gets different controls than before; the new navigator is someone who
 * navigated least so far. A {@linkplain Mode#REPAIR repair} (someone dropped out) keeps the
 * navigator and as many controls as possible where they were.
 */
final class Seating {

    /** How seats are dealt. */
    enum Mode {
        /** SEAT SHUFFLE: new roles for everyone. */
        SHUFFLE,
        /** Someone dropped out: change as little as possible. */
        REPAIR
    }

    /**
     * One seat.
     *
     * @param playerId who sits there
     * @param controls what they control (the navigator seat holds {@link Control#NAVIGATOR})
     */
    record Seat(String playerId, List<Control> controls) {

        Seat {
            controls = List.copyOf(controls);
        }
    }

    private static final List<Control> UTILITIES = List.of(HORN, WIPERS, HEADLIGHTS);

    /** A shuffle tries this many random deals and keeps the one with the fewest repeats. */
    private static final int SHUFFLE_ATTEMPTS = 24;

    private List<Seat> seats = List.of();
    private int version;
    private final Map<String, Integer> navigatorStints = new HashMap<>();

    /**
     * The seats for {@code players} active players, navigator first.
     *
     * @param players number of active players
     * @return one control list per seat (as many as players)
     */
    static List<List<Control>> layout(int players) {
        if (players <= 0) {
            return List.of();
        }
        int drivers = players >= 2 ? players - 1 : 1;
        List<List<Control>> driving = new ArrayList<>();
        for (List<Control> seat : drivingSeats(drivers)) {
            driving.add(new ArrayList<>(seat));
        }
        for (Control utility : UTILITIES) {
            if (driving.stream().noneMatch(seat -> seat.contains(utility))) {
                driving.stream()
                        .min(
                                Comparator.<List<Control>>comparingInt(List::size)
                                        .thenComparingInt(seat -> rank(seat.getFirst())))
                        .orElseThrow()
                        .add(utility);
            }
        }
        List<List<Control>> all = new ArrayList<>();
        if (players >= 2) {
            all.add(List.of(NAVIGATOR));
        }
        driving.forEach(seat -> all.add(List.copyOf(seat)));
        return List.copyOf(all);
    }

    private static List<List<Control>> drivingSeats(int drivers) {
        return switch (drivers) {
            case 1 -> List.of(List.of(STEER_LEFT, STEER_RIGHT, GAS, BRAKE));
            case 2 -> List.of(List.of(STEER_LEFT, STEER_RIGHT), List.of(GAS, BRAKE));
            case 3 -> List.of(List.of(STEER_LEFT), List.of(STEER_RIGHT), List.of(GAS, BRAKE));
            case 4 ->
                    List.of(
                            List.of(STEER_LEFT),
                            List.of(STEER_RIGHT),
                            List.of(GAS),
                            List.of(BRAKE));
            case 5, 6, 7 -> {
                List<List<Control>> seats =
                        new ArrayList<>(
                                List.of(
                                        List.of(STEER_LEFT),
                                        List.of(STEER_RIGHT),
                                        List.of(GAS),
                                        List.of(BRAKE)));
                for (int i = 0; i < drivers - 4; i++) {
                    seats.add(List.of(UTILITIES.get(i)));
                }
                yield seats;
            }
            case 8 ->
                    List.of(
                            List.of(STEER_LEFT),
                            List.of(STEER_LEFT),
                            List.of(STEER_RIGHT),
                            List.of(STEER_RIGHT),
                            List.of(GAS),
                            List.of(BRAKE),
                            List.of(HORN),
                            List.of(WIPERS, HEADLIGHTS));
            default ->
                    List.of(
                            List.of(STEER_LEFT),
                            List.of(STEER_LEFT),
                            List.of(STEER_RIGHT),
                            List.of(STEER_RIGHT),
                            List.of(GAS),
                            List.of(BRAKE),
                            List.of(HORN),
                            List.of(WIPERS),
                            List.of(HEADLIGHTS));
        };
    }

    /** Which seats take extra utilities first: utility seats, then brake, gas, right, left. */
    private static int rank(Control first) {
        return switch (first) {
            case HORN, WIPERS, HEADLIGHTS -> 0;
            case BRAKE -> 1;
            case GAS -> 2;
            case STEER_RIGHT -> 3;
            default -> 4;
        };
    }

    /**
     * Deals the seats to the given (connected) players.
     *
     * @param players player ids in join order
     * @param mode shuffle or repair
     * @param random the game's random source
     */
    void deal(List<String> players, Mode mode, RandomGenerator random) {
        List<List<Control>> layout = layout(players.size());
        Map<String, List<Control>> previous = new HashMap<>();
        seats.forEach(seat -> previous.put(seat.playerId(), seat.controls()));
        String oldNavigator = navigator();
        List<String> drivers = new ArrayList<>(players);
        String navigator = null;
        if (players.size() >= 2) {
            navigator = pickNavigator(drivers, oldNavigator, mode, random);
            drivers.remove(navigator);
            if (!navigator.equals(oldNavigator)) {
                navigatorStints.merge(navigator, 1, Integer::sum);
            }
        }
        String[] best = dealDrivers(layout, navigator, drivers, previous, mode, random);
        if (mode == Mode.SHUFFLE) {
            int bestOverlap = totalOverlap(best, layout, previous);
            for (int attempt = 1; attempt < SHUFFLE_ATTEMPTS && bestOverlap > 0; attempt++) {
                String[] owners = dealDrivers(layout, navigator, drivers, previous, mode, random);
                int overlap = totalOverlap(owners, layout, previous);
                if (overlap < bestOverlap) {
                    best = owners;
                    bestOverlap = overlap;
                }
            }
        }
        List<Seat> dealt = new ArrayList<>();
        for (int i = 0; i < layout.size(); i++) {
            dealt.add(new Seat(best[i], layout.get(i)));
        }
        seats = List.copyOf(dealt);
        version++;
    }

    private static int totalOverlap(
            String[] owners, List<List<Control>> layout, Map<String, List<Control>> previous) {
        int total = 0;
        for (int i = 0; i < owners.length; i++) {
            total += overlap(previous.getOrDefault(owners[i], List.of()), layout.get(i));
        }
        return total;
    }

    /** One greedy deal: every driving seat goes to a free player with the best overlap score. */
    private static String[] dealDrivers(
            List<List<Control>> layout,
            String navigator,
            List<String> drivers,
            Map<String, List<Control>> previous,
            Mode mode,
            RandomGenerator random) {
        String[] owners = new String[layout.size()];
        int first = 0;
        if (navigator != null) {
            owners[0] = navigator;
            first = 1;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = first; i < layout.size(); i++) {
            order.add(i);
        }
        if (mode == Mode.SHUFFLE) {
            shuffle(order, random);
        } else {
            order.sort(Comparator.comparingInt((Integer i) -> -layout.get(i).size()));
        }
        List<String> free = new ArrayList<>(drivers);
        for (int index : order) {
            String player = pickDriver(free, layout.get(index), previous, mode, random);
            owners[index] = player;
            free.remove(player);
        }
        return owners;
    }

    private String pickNavigator(
            List<String> candidates, String oldNavigator, Mode mode, RandomGenerator random) {
        if (mode == Mode.REPAIR && oldNavigator != null && candidates.contains(oldNavigator)) {
            return oldNavigator;
        }
        List<String> eligible = new ArrayList<>(candidates);
        if (mode == Mode.SHUFFLE && eligible.size() > 1) {
            eligible.remove(oldNavigator);
        }
        int fewest =
                eligible.stream().mapToInt(p -> navigatorStints.getOrDefault(p, 0)).min().orElse(0);
        List<String> best =
                eligible.stream()
                        .filter(p -> navigatorStints.getOrDefault(p, 0) == fewest)
                        .toList();
        return best.get(random.nextInt(best.size()));
    }

    private static String pickDriver(
            List<String> candidates,
            List<Control> seat,
            Map<String, List<Control>> previous,
            Mode mode,
            RandomGenerator random) {
        int bestScore = Integer.MAX_VALUE;
        List<String> best = new ArrayList<>();
        for (String player : candidates) {
            int overlap = overlap(previous.getOrDefault(player, List.of()), seat);
            int score = mode == Mode.SHUFFLE ? overlap : -overlap;
            if (score < bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(player);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    private static int overlap(List<Control> before, List<Control> seat) {
        int shared = 0;
        for (Control control : seat) {
            if (before.contains(control)) {
                shared++;
            }
        }
        return shared;
    }

    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }

    /** The seats in display order: navigator first, then the driving seats. */
    List<Seat> seats() {
        return seats;
    }

    /** Increases with every deal; frames carry it so browsers match pressed masks to seats. */
    int version() {
        return version;
    }

    /** The controls of a player (empty without a seat). */
    List<Control> controlsOf(String playerId) {
        for (Seat seat : seats) {
            if (seat.playerId().equals(playerId)) {
                return seat.controls();
            }
        }
        return List.of();
    }

    /** Whether the player has a seat. */
    boolean seated(String playerId) {
        return seats.stream().anyMatch(seat -> seat.playerId().equals(playerId));
    }

    /** Whether the player holds the control right now. */
    boolean holds(String playerId, Control control) {
        return controlsOf(playerId).contains(control);
    }

    /** Everyone holding a control (two for power steering). */
    List<String> holders(Control control) {
        return seats.stream()
                .filter(seat -> seat.controls().contains(control))
                .map(Seat::playerId)
                .toList();
    }

    /** The current navigator, or {@code null}. */
    String navigator() {
        List<String> navigators = holders(NAVIGATOR);
        return navigators.isEmpty() ? null : navigators.getFirst();
    }

    /** How often a player became navigator in this game. */
    int navigatorStints(String playerId) {
        return navigatorStints.getOrDefault(playerId, 0);
    }
}
