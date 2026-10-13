package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.bus;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.holders;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.input;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.FrameView;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The bus's physics, played through the engine: the pedal and steering holders send {@code input}
 * actions, time advances, and the bus is read from the debug state and the streamed frames.
 */
class DrivingTest {

    private static double x(GameHarness game) {
        return bus(game).get("x");
    }

    private static double speed(GameHarness game) {
        return bus(game).get("speed");
    }

    private static double heading(GameHarness game) {
        return bus(game).get("heading");
    }

    private static List<FrameView> frames(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.FRAME_EVENT))
                .map(e -> (FrameView) e.payload())
                .toList();
    }

    private static boolean anyFrameWith(GameHarness game, int effect) {
        return frames(game).stream().anyMatch(frame -> (frame.e() & effect) != 0);
    }

    @Test
    void gasAcceleratesToTheTopSpeed() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1);
        assertThat(speed(game)).isCloseTo(4.0, within(1e-9));
        assertThat(x(game)).isCloseTo(5.5 + 2.1, within(1e-6)); // ∑ 0.2·k·0.05, k = 1…20

        game.advanceSeconds(1);
        assertThat(speed(game)).isEqualTo(BusPhysics.TOP_SPEED);
        assertThat(bus(game).get("y")).isEqualTo(3.5);
    }

    @Test
    void brakeStopsTheBusThenReversesSlowly() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1.5);
        assertThat(speed(game)).isEqualTo(6.0);

        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(0.7); // 9 tiles/s² → stopped after 0.67 s
        assertThat(speed(game)).isZero();

        game.advanceSeconds(0.05);
        assertThat(speed(game)).as("reversing").isNegative();
        double x = x(game);
        game.advanceSeconds(1);
        assertThat(speed(game)).isEqualTo(-BusPhysics.REVERSE_TOP_SPEED);
        assertThat(x(game)).isLessThan(x);
    }

    @Test
    void brakeWinsOverGas() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1);
        press(game, Control.BRAKE, true).advanceSeconds(0.2);
        assertThat(speed(game)).isCloseTo(4.0 - 4 * 0.45, within(1e-9));
    }

    @Test
    void withoutPedalsTheBusRollsOut() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(0.5);
        press(game, Control.GAS, false).advanceSeconds(0.5);
        assertThat(speed(game)).isCloseTo(2.0 - 1.0, within(1e-9));
        game.advanceSeconds(1);
        assertThat(speed(game)).isZero();
    }

    @Test
    void steeringTurnsInProportionToTheSpeed() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.STEER_LEFT, true).advanceSeconds(0.5);
        assertThat(heading(game)).as("no turning while stopped").isZero();

        press(game, Control.GAS, true).advanceSeconds(0.25); // speed 1 → half the turn rate
        double slow = -heading(game);
        game.advanceSeconds(0.25); // speed 2 → full rate from here on
        double faster = -heading(game) - slow;
        assertThat(slow).isPositive();
        assertThat(faster).isGreaterThan(slow);
        assertThat(bus(game).get("y")).as("left = north when driving east").isLessThan(3.5);
    }

    @Test
    void steeringRightTurnsTheOtherWayAndBothSidesCancelOut() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(0.6);
        press(game, Control.STEER_RIGHT, true).advanceSeconds(0.1);
        double right = heading(game);
        assertThat(right).isCloseTo(BusPhysics.TURN_RATE * 0.1, within(1e-9));

        press(game, Control.STEER_LEFT, true).advanceSeconds(0.3);
        assertThat(heading(game)).isEqualTo(right);
    }

    @Test
    void steeringIsMirroredWhenReversing() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1);
        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(1);
        assertThat(speed(game)).isNegative();
        press(game, Control.STEER_LEFT, true).advanceSeconds(0.2);
        assertThat(heading(game)).as("left while reversing swings the nose right").isPositive();
    }

    @Test
    void grassSlowsTheBusDown() {
        GameHarness game =
                TestGames.start(
                        TestGames.lanes(
                                Map.of(
                                        14, '.', 15, '.', 16, '.', 17, '.', 18, '.', 19, '.', 20,
                                        '.', 21, '.', 22, '.', 23, '.')));
        press(game, Control.GAS, true).advanceSeconds(3); // full speed when entering the grass
        assertThat(x(game)).isGreaterThan(15);
        assertThat(speed(game)).isEqualTo(BusPhysics.OFFROAD_TOP_SPEED);
        assertThat(frames(game).getLast().e() & Simulation.OFFROAD).isNotZero();
    }

    @Test
    void aWallStopsTheBusWithABump() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(15, 'T')));
        press(game, Control.GAS, true).advanceSeconds(3);
        assertThat(x(game)).isLessThanOrEqualTo(15 - Bus.RADIUS).isGreaterThan(14);
        assertThat(speed(game)).isLessThan(0.21);
        assertThat(anyFrameWith(game, Simulation.BUMP)).isTrue();
    }

    @Test
    void aGlancingHitScrapesAlongTheWall() {
        String[] rows = TestGames.STRIP.clone();
        rows[2] = "#rrrr" + "T".repeat(41) + "rrr#"; // a hedge right above the bus's lane
        GameHarness game = TestGames.start(TestGames.map("Avenue", rows));
        press(game, Control.GAS, true).advanceSeconds(0.8);
        press(game, Control.STEER_LEFT, true).advanceSeconds(0.1);
        press(game, Control.STEER_LEFT, false).advanceSeconds(1);

        assertThat(anyFrameWith(game, Simulation.SCRAPE)).isTrue();
        assertThat(anyFrameWith(game, Simulation.BUMP)).isFalse();
        assertThat(x(game)).isGreaterThan(9);
        assertThat(bus(game).get("y")).isGreaterThanOrEqualTo(3 + Bus.RADIUS);
    }

    @Test
    void rushHourRaisesTheTopSpeedAndWeakensTheBrakes() {
        GameHarness game =
                TestGames.start(
                        List.of(TestGames.strip(Map.of())),
                        Map.of("variation", "rush-hour"),
                        "ann",
                        "bob",
                        "cleo");
        game.advanceSeconds(60);
        press(game, Control.GAS, true).advanceSeconds(2);
        assertThat(speed(game)).isGreaterThan(6.9).isLessThan(7.1);

        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(0.8);
        assertThat(speed(game)).as("a classic bus would stand by now").isPositive();
    }

    @Test
    void powerSteeringNeedsBothHoldersOfASide() {
        GameHarness game =
                TestGames.start(
                        List.of(TestGames.strip(Map.of())),
                        Map.of(),
                        "p1",
                        "p2",
                        "p3",
                        "p4",
                        "p5",
                        "p6",
                        "p7",
                        "p8",
                        "p9",
                        "p10");
        List<String> left = holders(game, Control.STEER_LEFT);
        assertThat(left).hasSize(2);
        press(game, Control.GAS, true).advanceSeconds(0.6);

        game.act(left.getFirst(), "input", input(Control.STEER_LEFT, true)).advanceSeconds(0.3);
        assertThat(heading(game)).as("one of two holders").isZero();

        game.act(left.getLast(), "input", input(Control.STEER_LEFT, true)).advanceSeconds(0.3);
        assertThat(heading(game)).as("both together").isNegative();
    }

    @Test
    void reversingFromTheStartHitsTheCeosCarWithinAboutASecond() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.BRAKE, true).advanceSeconds(0.8);
        assertThat(game.lastTurn()).as("a short tap is survivable").isNull();
        game.advanceSeconds(0.4);
        assertThat(game.lastTurn().headline()).contains("CEO");
    }
}
