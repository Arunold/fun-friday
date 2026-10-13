package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.bus;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.tap;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.AreaView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.FrameView;
import com.example.funfridaygame.testkit.GameHarness;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The hazards that need a specific control, played through the engine: geese and the horn, rain and
 * the wipers, tunnels and the headlights.
 */
class HazardsTest {

    private static FrameView lastFrame(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.FRAME_EVENT))
                .map(e -> (FrameView) e.payload())
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static boolean effect(GameHarness game, int bit) {
        return (lastFrame(game).e() & bit) != 0;
    }

    @SuppressWarnings("unchecked")
    private static String gooseState(GameHarness game) {
        Map<String, Object> hazards = (Map<String, Object>) game.debugState().get("hazards");
        List<Map<String, Object>> geese = (List<Map<String, Object>>) hazards.get("geese");
        return (String) geese.getFirst().get("state");
    }

    private static double x(GameHarness game) {
        return bus(game).get("x");
    }

    /** Holds GAS until the bus centre passes {@code x} (checked every tick). */
    private static void driveTo(GameHarness game, double x) {
        press(game, Control.GAS, true);
        for (int i = 0; i < 200 && x(game) < x; i++) {
            game.advance(Duration.ofMillis(LevelRun.TICK_MILLIS));
        }
    }

    // ------------------------------------------------------------------ geese

    @Test
    void geeseCrossWhenTheBusComesCloseAndBlockTheRoadForThreeSeconds() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(16, 'G')));
        driveTo(game, 10);
        assertThat(gooseState(game)).isEqualTo("IDLE");

        game.advanceSeconds(1.5);
        assertThat(gooseState(game)).isEqualTo("CROSSING");
        assertThat(lastFrame(game).g()).as("flock 0 crossing").isEqualTo(1);
        assertThat(effect(game, Simulation.GEESE)).isTrue();
        assertThat(x(game)).as("stopped in front of the geese").isLessThan(16 - Bus.RADIUS + 1e-9);
        assertThat(game.streamed())
                .anyMatch(
                        e -> e.payload() instanceof FrameView f && (f.e() & Simulation.BUMP) != 0);

        game.advanceSeconds(2);
        assertThat(gooseState(game)).isEqualTo("GONE");
        AreaView window = latestWindow(game);
        assertThat(window.geese()).as("gone from the drivers' window").isEmpty();
        game.advanceSeconds(1);
        assertThat(x(game)).isGreaterThan(17);
    }

    @Test
    void aHonkScattersTheGeeseAtOnce() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(16, 'G')));
        driveTo(game, 10.6);
        assertThat(gooseState(game)).as("5–6 tiles away: not crossing yet").isEqualTo("IDLE");

        tap(game, Control.HORN).advance(Duration.ofMillis(LevelRun.TICK_MILLIS));
        assertThat(gooseState(game)).isEqualTo("GONE");
        assertThat(effect(game, Simulation.HONK)).isTrue();

        game.advanceSeconds(1.5);
        assertThat(x(game)).isGreaterThan(17);
        assertThat(game.streamed())
                .noneMatch(
                        e -> e.payload() instanceof FrameView f && (f.e() & Simulation.BUMP) != 0);
    }

    @Test
    void honkingFarAwayDoesNothing() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(30, 'G')));
        tap(game, Control.HORN).advanceSeconds(0.1);
        assertThat(gooseState(game)).isEqualTo("IDLE");
        assertThat(effect(game, Simulation.HONK)).as("but you hear it").isTrue();
    }

    @Test
    void geeseInSightAreInTheDriversWindow() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(12, 'G')));
        driveTo(game, 6.5);
        AreaView window = latestWindow(game);
        assertThat(window.geese()).singleElement().satisfies(g -> assertThat(g.cells()).hasSize(3));
    }

    // ------------------------------------------------------------------ rain

    @Test
    void rainBlursTheViewUnlessTheWipersWereTappedInTheLastSecondAndAHalf() {
        GameHarness game =
                TestGames.start(TestGames.lanes(Map.of(7, '~', 8, '~', 9, '~', 10, '~', 11, '~')));
        driveTo(game, 7.5);
        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(0.5);
        press(game, Control.BRAKE, false).advanceSeconds(0.2);
        assertThat(effect(game, Simulation.RAIN)).isTrue();
        assertThat(effect(game, Simulation.BLUR)).isTrue();

        tap(game, Control.WIPERS).advanceSeconds(0.1); // wiped at the next tick, 50 ms later
        assertThat(effect(game, Simulation.BLUR)).isFalse();
        assertThat(effect(game, Simulation.WIPE)).isTrue();
        game.advanceSeconds(1.4); // 1.45 s after the wipe
        assertThat(effect(game, Simulation.BLUR)).isFalse();
        game.advanceSeconds(0.1); // 1.55 s after the wipe
        assertThat(effect(game, Simulation.BLUR)).isTrue();
    }

    // ------------------------------------------------------------------ tunnels

    @Test
    void tunnelsAreDarkUntilTheHeadlightsAreSwitchedOn() {
        GameHarness game =
                TestGames.start(
                        TestGames.lanes(Map.of(7, 't', 8, 't', 9, 't', 10, 't', 11, 't', 12, 't')));
        driveTo(game, 8);
        press(game, Control.GAS, false);
        assertThat(effect(game, Simulation.TUNNEL)).isTrue();
        assertThat(effect(game, Simulation.DARK)).isTrue();

        tap(game, Control.HEADLIGHTS).advanceSeconds(0.1);
        assertThat(effect(game, Simulation.DARK)).isFalse();
        assertThat(effect(game, Simulation.LIGHTS)).isTrue();
        assertThat(game.debugState().get("headlights")).isEqualTo(true);

        tap(game, Control.HEADLIGHTS).advanceSeconds(0.1);
        assertThat(effect(game, Simulation.DARK)).as("switched off again").isTrue();
    }

    @Test
    void tapsLightUpTheirControlOnEveryonesRoster() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        tap(game, Control.HORN).advanceSeconds(0.1);
        FrameView frame = lastFrame(game);
        int pressed = 0;
        for (int mask : frame.p()) {
            pressed |= mask;
        }
        assertThat(pressed & Control.HORN.bit()).isNotZero();
        game.advanceSeconds(0.5);
        int later = 0;
        for (int mask : lastFrame(game).p()) {
            later |= mask;
        }
        assertThat(later).isZero();
    }

    private static AreaView latestWindow(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.WINDOW_EVENT))
                .map(e -> (AreaView) e.payload())
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
