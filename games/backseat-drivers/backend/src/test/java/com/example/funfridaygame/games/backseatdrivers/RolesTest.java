package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.bus;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.holder;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.holders;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.input;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.level;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.navigator;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.roles;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.FrameView;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Who controls what (GameIdeas.md "Who Controls What"), the SEAT SHUFFLE and players dropping out,
 * played through the engine.
 */
class RolesTest {

    private static final List<String> EVERY_CONTROL =
            List.of(
                    "NAVIGATOR",
                    "STEER_LEFT",
                    "STEER_RIGHT",
                    "GAS",
                    "BRAKE",
                    "HORN",
                    "WIPERS",
                    "HEADLIGHTS");

    private static String[] names(int players) {
        return IntStream.rangeClosed(1, players).mapToObj(i -> "p" + i).toArray(String[]::new);
    }

    private static GameHarness game(int players) {
        return TestGames.start(List.of(TestGames.strip(Map.of())), Map.of(), names(players));
    }

    private static int version(GameHarness game) {
        return (Integer) game.debugState().get("rolesVersion");
    }

    private static boolean warning(GameHarness game, String viewer) {
        return (Boolean) level(game.view(viewer)).get("shuffleWarning");
    }

    // ------------------------------------------------------------------ who controls what

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10})
    void everyoneHasASeatAndEveryControlIsCovered(int players) {
        GameHarness game = game(players);
        Map<String, List<String>> roles = roles(game);

        assertThat(roles).hasSize(players);
        assertThat(roles.values().stream().flatMap(List::stream)).containsAll(EVERY_CONTROL);
        assertThat(holders(game, Control.NAVIGATOR)).hasSize(1);
        assertThat(roles.values()).allSatisfy(seat -> assertThat(seat).isNotEmpty());
        assertThat(roles.values().stream().mapToInt(List::size).max().orElseThrow())
                .as("utilities are spread evenly")
                .isLessThanOrEqualTo(players == 3 ? 4 : 3);
        roles.forEach(
                (id, controls) ->
                        assertThat(level(game.view(id)).get("myControls")).isEqualTo(controls));
        int steering = players >= 9 ? 2 : 1;
        assertThat(holders(game, Control.STEER_LEFT)).as("power steering").hasSize(steering);
        assertThat(holders(game, Control.STEER_RIGHT)).hasSize(steering);
    }

    @Test
    void theSeatsFollowTheTableInGameIdeas() {
        assertThat(roles(game(3)).values())
                .containsExactlyInAnyOrder(
                        List.of("NAVIGATOR"),
                        List.of("STEER_LEFT", "STEER_RIGHT", "WIPERS"),
                        List.of("GAS", "BRAKE", "HORN", "HEADLIGHTS"));
        assertThat(roles(game(4)).values())
                .containsExactlyInAnyOrder(
                        List.of("NAVIGATOR"),
                        List.of("STEER_LEFT", "WIPERS"),
                        List.of("STEER_RIGHT", "HORN"),
                        List.of("GAS", "BRAKE", "HEADLIGHTS"));
        assertThat(roles(game(5)).values())
                .containsExactlyInAnyOrder(
                        List.of("NAVIGATOR"),
                        List.of("STEER_LEFT"),
                        List.of("STEER_RIGHT", "HEADLIGHTS"),
                        List.of("GAS", "WIPERS"),
                        List.of("BRAKE", "HORN"));
        assertThat(roles(game(8)).values())
                .containsExactlyInAnyOrder(
                        List.of("NAVIGATOR"),
                        List.of("STEER_LEFT"),
                        List.of("STEER_RIGHT"),
                        List.of("GAS"),
                        List.of("BRAKE"),
                        List.of("HORN"),
                        List.of("WIPERS"),
                        List.of("HEADLIGHTS"));
    }

    @Test
    void theSeatsAreListedInEveryViewNavigatorFirst() {
        GameHarness game = game(4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seats =
                (List<Map<String, Object>>) level(game.spectatorView()).get("seats");
        assertThat(seats).hasSize(4);
        assertThat(seats.getFirst().get("playerId")).isEqualTo(navigator(game));
        assertThat(seats.getFirst().get("controls")).isEqualTo(List.of("NAVIGATOR"));
    }

    // ------------------------------------------------------------------ SEAT SHUFFLE

    @Test
    void aSeatShuffleWarnsTwoSecondsAheadThenDealsNewRoles() {
        GameHarness game = game(3);
        long start = game.now();
        String navigator = navigator(game);
        Map<String, List<String>> before = roles(game);
        int version = version(game);

        game.advanceSeconds(27.95);
        assertThat(warning(game, "p1")).isFalse();
        int changes = game.changes();
        game.advanceSeconds(0.1);
        assertThat(warning(game, "p1")).as("the siren").isTrue();
        assertThat(game.changes()).as("a snapshot announces it").isGreaterThan(changes);
        assertThat(version(game)).isEqualTo(version);

        game.advanceSeconds(1.4);
        press(game, Control.GAS, true).advanceSeconds(0.5);
        double speed = bus(game).get("speed");
        game.advanceSeconds(0.1); // the shuffle happens at 30 s
        assertThat(version(game)).isEqualTo(version + 1);
        assertThat(navigator(game)).as("the navigator changes").isNotEqualTo(navigator);
        assertThat(warning(game, "p1")).isFalse();
        assertThat(level(game.view("p1")).get("nextShuffleAt")).isEqualTo(start + 60_000);
        Map<String, List<String>> after = roles(game);
        before.forEach(
                (id, controls) ->
                        assertThat(after.get(id)).as("new job for " + id).isNotEqualTo(controls));
        assertThat(bus(game).get("speed")).as("the gas was released").isLessThan(speed);
        FrameView frame = lastFrame(game);
        assertThat(frame.r()).isEqualTo(version + 1);
        assertThat(frame.p()).containsOnly(0);
    }

    @Test
    void aStalePressFromTheFormerHolderIsIgnored() {
        GameHarness game = game(3);
        String gas = holder(game, Control.GAS);
        game.advanceSeconds(30.05);
        String newGas = holder(game, Control.GAS);
        assertThat(newGas).isNotEqualTo(gas);

        game.act(gas, "input", input(Control.GAS, true)).advanceSeconds(1);
        assertThat(bus(game).get("speed")).isZero();
        game.act(newGas, "input", input(Control.GAS, true)).advanceSeconds(0.5);
        assertThat(bus(game).get("speed")).isPositive();
    }

    @Test
    void everyoneGetsToNavigate() {
        GameHarness game =
                TestGames.start(
                        List.of(TestGames.strip(Map.of())),
                        Map.of("shuffle", 20),
                        "p1",
                        "p2",
                        "p3",
                        "p4");
        Set<String> navigators = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            navigators.add(navigator(game));
            game.advanceSeconds(20);
        }
        assertThat(navigators).containsExactlyInAnyOrder("p1", "p2", "p3", "p4");
    }

    // ------------------------------------------------------------------ dropping out

    @Test
    void aNavigatorLosingTheConnectionIsReplacedAtOnce() {
        GameHarness game = game(4);
        String navigator = navigator(game);
        int version = version(game);

        game.disconnect(navigator).advanceSeconds(0.05);

        assertThat(navigator(game)).isNotNull().isNotEqualTo(navigator);
        assertThat(roles(game)).hasSize(3).doesNotContainKey(navigator);
        assertThat(roles(game).values().stream().flatMap(List::stream)).containsAll(EVERY_CONTROL);
        assertThat(version(game)).isEqualTo(version + 1);

        game.reconnect(navigator).advanceSeconds(0.05);
        assertThat(level(game.view(navigator)).get("myControls"))
                .as("back without a seat until the next shuffle")
                .isEqualTo(List.of());
        game.advanceSeconds(30);
        assertThat(roles(game)).containsKey(navigator).hasSize(4);
    }

    @Test
    void aDriverLeavingHandsTheirControlsOverImmediately() {
        GameHarness game = game(4);
        String navigator = navigator(game);
        String right = holder(game, Control.STEER_RIGHT);
        String left = holder(game, Control.STEER_LEFT);

        game.leave(right);

        assertThat(navigator(game)).as("a repair keeps the navigator").isEqualTo(navigator);
        assertThat(roles(game).get(left)).contains("STEER_LEFT", "STEER_RIGHT");
        assertThat(roles(game)).hasSize(3);
    }

    @Test
    void theGasIsReleasedWhenItsHolderDropsOut() {
        GameHarness game = game(3);
        String gas = holder(game, Control.GAS);
        press(game, Control.GAS, true).advanceSeconds(1);
        double speed = bus(game).get("speed");

        game.disconnect(gas).advanceSeconds(0.5);

        assertThat(holder(game, Control.GAS)).isNotEqualTo(gas);
        assertThat(bus(game).get("speed")).isLessThan(speed);
    }

    @Test
    void inputsFromADisconnectedPlayerAreIgnored() {
        GameHarness game = game(5);
        String brake = holder(game, Control.BRAKE);
        game.disconnect(brake);
        game.act(brake, "input", input(Control.BRAKE, true)).advanceSeconds(1.5);
        assertThat(game.lastTurn()).as("no reversing into the CEO's car").isNull();
    }

    private static FrameView lastFrame(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.FRAME_EVENT))
                .map(e -> (FrameView) e.payload())
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
