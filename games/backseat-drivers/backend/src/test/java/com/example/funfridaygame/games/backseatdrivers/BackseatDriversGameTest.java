package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.bus;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.holder;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.input;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.level;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.navigator;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.AreaView;
import com.example.funfridaygame.games.backseatdrivers.BackseatDriversView.FrameView;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.testkit.GameHarness;
import com.example.funfridaygame.testkit.GameHarness.Event;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests of a level through the real engine with virtual time: driving, hazards and the
 * deadline, the ways a level ends, team scoring, streaming and hidden information.
 */
class BackseatDriversGameTest {

    private static final long LEVEL_MILLIS = 120_000;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> frame(Map<String, Object> view) {
        return (Map<String, Object>) level(view).get("frame");
    }

    private static List<FrameView> frames(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.FRAME_EVENT))
                .map(e -> (FrameView) e.payload())
                .toList();
    }

    private static List<AreaView> windows(GameHarness game) {
        return game.streamed().stream()
                .filter(e -> e.type().equals(LevelRun.WINDOW_EVENT))
                .map(e -> (AreaView) e.payload())
                .toList();
    }

    // ------------------------------------------------------------------ start & driving

    @Test
    void aLevelStartsWithEveryoneSeatedAndTheBusInTheGarage() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));

        Map<String, List<String>> roles = TestGames.roles(game);
        assertThat(roles).hasSize(3);
        assertThat(roles.values())
                .containsExactlyInAnyOrder(
                        List.of("NAVIGATOR"),
                        List.of("STEER_LEFT", "STEER_RIGHT", "WIPERS"),
                        List.of("GAS", "BRAKE", "HORN", "HEADLIGHTS"));
        Map<String, Object> view = game.view(holder(game, Control.GAS));
        Map<String, Object> level = level(view);
        assertThat(view.get("mode")).isEqualTo("LEVEL");
        assertThat(level.get("status")).isEqualTo("DRIVING");
        assertThat(level.get("name")).isEqualTo("Strip");
        assertThat(level.get("myControls"))
                .isEqualTo(List.of("GAS", "BRAKE", "HORN", "HEADLIGHTS"));
        assertThat(level.get("navigator")).isEqualTo(false);
        assertThat(level.get("nextShuffleAt")).isEqualTo(game.now() + 30_000);
        assertThat(frame(view).get("t")).isEqualTo(0L);
        assertThat(frame(view).get("b")).isEqualTo(List.of(5500, 3500, 0, 0));
        assertThat(game.remainingMillis()).isEqualTo(LEVEL_MILLIS);
        assertThat(game.debugState().get("ceoCars")).isNotNull();
    }

    @Test
    void ticksStreamFramesWithoutSnapshots() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        int changes = game.changes();

        press(game, Control.GAS, true).advanceSeconds(1);

        assertThat(game.changes()).as("no snapshot for inputs or ticks").isEqualTo(changes);
        List<FrameView> frames = frames(game);
        assertThat(frames).hasSize(20);
        assertThat(frames).extracting(FrameView::t).startsWith(1L, 2L, 3L).endsWith(20L);
        FrameView last = frames.getLast();
        assertThat(last.b()[0]).isGreaterThan(5500 + 1500);
        assertThat(last.b()[3]).isEqualTo(4000);
        assertThat(last.l()).isEqualTo(1);
        int gasSeat = seatIndex(game, holder(game, Control.GAS));
        assertThat(last.p()[gasSeat] & Control.GAS.bit()).isNotZero();
        assertThat(bus(game).get("x")).isGreaterThan(7.5);
        assertThat(frame(game.view("ann")).get("t"))
                .as("the view carries the newest frame")
                .isEqualTo(20L);
    }

    @Test
    void inputsFromPlayersWithoutThatControlAreIgnoredSilently() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        String gas = holder(game, Control.GAS);
        String steering = holder(game, Control.STEER_LEFT);

        game.act(navigator(game), "input", input(Control.GAS, true));
        game.act(steering, "input", input(Control.GAS, true));
        game.act(steering, "input", input(Control.HORN, true));
        game.advanceSeconds(1);

        assertThat(bus(game).get("x")).isEqualTo(5.5);
        assertThat(game.lastTurn()).isNull();
        game.act(gas, "input", input(Control.GAS, true)).advanceSeconds(0.5);
        assertThat(bus(game).get("x")).isGreaterThan(5.5);
    }

    @Test
    void invalidActionsAreRejectedWithAMessage() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        String gas = holder(game, Control.GAS);

        assertThat(game.reject(gas, "jump", Map.of()).getMessage()).contains("Unknown action");
        assertThat(
                        game.reject(gas, "input", Map.of("control", "TURBO", "pressed", true))
                                .getMessage())
                .contains("Invalid");
        assertThat(game.reject(gas, "input", Map.of("control", "GAS")).getMessage())
                .contains("pressed");
        assertThat(game.reject(gas, "input", input(Control.NAVIGATOR, true)).getMessage())
                .contains("shout");
        assertThat(game.reject(gas, "signal", Map.of("kind", "LEFT")).getMessage())
                .contains("silent navigator");
        assertThat(game.reject(gas, "vote", Map.of("playerId", "bob")).getMessage())
                .contains("after the last level");
    }

    @Test
    void steeringTurnsTheBus() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1);
        press(game, Control.STEER_RIGHT, true).advanceSeconds(0.2);
        assertThat(bus(game).get("heading")).isPositive();
        press(game, Control.STEER_LEFT, true);
        double heading = bus(game).get("heading");
        game.advanceSeconds(0.2);
        assertThat(bus(game).get("heading")).as("both sides cancel out").isEqualTo(heading);
    }

    // ------------------------------------------------------------------ hazards & deadline

    @Test
    void coffeeGivesFiveSecondsAndAConeCostsFive() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(9, 'K', 15, 'C')));
        long deadline = game.now() + LEVEL_MILLIS;
        press(game, Control.GAS, true);
        int changes = game.changes();

        game.advanceSeconds(1.4); // past the coffee at x = 9
        assertThat(game.now() + game.remainingMillis()).isEqualTo(deadline + 5000);
        assertThat(game.changes()).as("the timer changed: a snapshot").isGreaterThan(changes);
        assertThat(frame(game.view("ann")).get("k")).isEqualTo(1);

        game.advanceSeconds(1.2); // past the cone at x = 15
        assertThat(game.now() + game.remainingMillis()).isEqualTo(deadline);
        assertThat(frame(game.view("ann")).get("c")).isEqualTo(1);
        assertThat(game.debugState().get("conesHit")).isEqualTo(1);
    }

    @Test
    void conesCanEndTheLevelWhenTimeIsShort() {
        GameHarness game =
                TestGames.start(
                        List.of(TestGames.strip(Map.of(16, 'C'))),
                        Map.of("roundDuration", 90),
                        "ann",
                        "bob",
                        "cleo");
        game.advanceSeconds(86);
        press(game, Control.GAS, true).advanceSeconds(2.5);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("Out of time!");
    }

    // ------------------------------------------------------------------ the end of a level

    @Test
    void theCeosCarEndsTheLevel() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.BRAKE, true).advanceSeconds(1.5);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("The CEO's car! Level over.");
        assertThat(game.lastTurn().points()).containsOnlyKeys("ann", "bob", "cleo");
        assertThat(game.lastTurn().points().values()).containsOnly(0);
        Map<String, Object> level = level(game.view("bob"));
        assertThat(level.get("status")).isEqualTo("CRASHED");
        assertThat(level.get("result")).isNotNull();
        assertThat(frames(game).getLast().b()[0])
                .as("the last frame shows the crash")
                .isLessThan(5000);
    }

    @Test
    void parkingEndsTheLevelWithTheTimeBonusForEveryone() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(9, 'K', 12, 'P', 13, 'P')));
        game.advanceSeconds(10);
        press(game, Control.GAS, true).advanceSeconds(3);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        // parked after ~11.6 s with the coffee's 5 s: 125 − 11.6 → 114 s (rounded up)
        assertThat(game.lastTurn().headline()).isEqualTo("Parked with 114 s to spare! +1190");
        assertThat(game.lastTurn().points())
                .containsExactlyInAnyOrderEntriesOf(Map.of("ann", 1190, "bob", 1190, "cleo", 1190));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) level(game.view("cleo")).get("result");
        assertThat(result)
                .containsEntry("status", "PARKED")
                .containsEntry("secondsLeft", 114)
                .containsEntry("coffees", 1)
                .containsEntry("cones", 0)
                .containsEntry("score", 1190);
        assertThat(game.scores())
                .containsOnly(
                        Map.entry("ann", 1190), Map.entry("bob", 1190), Map.entry("cleo", 1190));
    }

    @Test
    void outOfTimeOnlyTheCoffeeCounts() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(8, 'K')));
        press(game, Control.GAS, true).advanceSeconds(1);
        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(0.5);
        press(game, Control.BRAKE, false);

        game.finishTurn();

        assertThat(game.lastTurn().headline()).isEqualTo("Out of time! Coffee still pays: +50");
        assertThat(game.lastTurn().points().values()).containsOnly(50);
        assertThat(level(game.view("ann")).get("status")).isEqualTo("TIME_UP");
    }

    @Test
    void disconnectedPlayersStillShareTheTeamScoreButThoseWhoLeftDoNot() {
        GameHarness game =
                TestGames.start(
                        List.of(TestGames.strip(Map.of(10, 'P'))),
                        Map.of(),
                        "ann",
                        "bob",
                        "cleo",
                        "dan");
        String gas = holder(game, Control.GAS);
        List<String> others =
                List.of("ann", "bob", "cleo", "dan").stream()
                        .filter(id -> !id.equals(gas))
                        .toList();
        game.disconnect(others.get(0)).leave(others.get(1));
        game.advanceSeconds(0.1);
        press(game, Control.GAS, true).advanceSeconds(3);

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().points())
                .containsKeys(gas, others.get(0), others.get(2))
                .doesNotContainKey(others.get(1));
    }

    // ------------------------------------------------------------------ hidden information

    @Test
    void onlyTheNavigatorSeesTheWholeMap() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(30, 'C', 40, 'K')));
        String navigator = navigator(game);
        String driver = holder(game, Control.GAS);

        Map<String, Object> navigatorLevel = level(game.view(navigator));
        assertThat(navigatorLevel.get("navigator")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) navigatorLevel.get("map");
        assertThat(map.get("w")).isEqualTo(50);
        assertThat((List<?>) map.get("rows")).hasSize(7);
        assertThat((List<?>) map.get("cones")).hasSize(1);
        assertThat(navigatorLevel.get("window")).isNull();

        for (Map<String, Object> view : List.of(game.view(driver), game.spectatorView())) {
            Map<String, Object> level = level(view);
            assertThat(level.get("map")).isNull();
            assertThat(level.get("navigator")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> window = (Map<String, Object>) level.get("window");
            assertThat((Integer) window.get("w")).isLessThanOrEqualTo(15);
            assertThat((List<?>) window.get("cones")).as("the cone is far away").isEmpty();
            @SuppressWarnings("unchecked")
            List<String> rows = (List<String>) window.get("rows");
            assertThat(String.join("", rows)).contains("?");
        }
        assertThat(level(game.spectatorView()).get("myControls")).isNull();
    }

    @Test
    void theStreamNeverCarriesMapDataBeyondTheWindow() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(30, 'C', 40, 'K')));
        press(game, Control.GAS, true).advanceSeconds(3);

        List<AreaView> windows = windows(game);
        assertThat(windows).isNotEmpty();
        assertThat(game.streamed())
                .extracting(Event::type)
                .containsOnly(LevelRun.FRAME_EVENT, LevelRun.WINDOW_EVENT);
        for (AreaView window : windows) {
            assertThat(window.w()).isLessThanOrEqualTo(15);
            assertThat(window.h()).isLessThanOrEqualTo(15);
            int cx = 0;
            for (int y = 0; y < window.h(); y++) {
                String row = window.rows().get(y);
                for (int x = 0; x < window.w(); x++) {
                    if (row.charAt(x) != Areas.UNKNOWN) {
                        cx++;
                    }
                }
            }
            assertThat(cx).isLessThan(15 * 15);
        }
    }

    @Test
    void windowsAreStreamedWhenTheBusEntersANewCell() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of(14, 'C')));
        press(game, Control.GAS, true).advanceSeconds(1.5);

        List<AreaView> windows = windows(game);
        assertThat(windows).extracting(AreaView::v).isSorted().doesNotHaveDuplicates();
        assertThat(windows).hasSizeGreaterThanOrEqualTo(4);
        AreaView latest = windows.getLast();
        @SuppressWarnings("unchecked")
        Map<String, Object> inView =
                (Map<String, Object>) level(game.view(holder(game, Control.GAS))).get("window");
        assertThat(inView.get("v")).isEqualTo(latest.v());
        assertThat(inView.get("x0")).isEqualTo(latest.x0());
        assertThat(latest.cones()).as("the cone ahead is in sight").hasSize(1);
    }

    @Test
    void everyoneSeesTheFullMapAndTheTrailOnceTheLevelIsOver() {
        GameHarness game = TestGames.start(TestGames.strip(Map.of()));
        press(game, Control.GAS, true).advanceSeconds(1);
        press(game, Control.GAS, false);
        press(game, Control.BRAKE, true).advanceSeconds(5);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);

        for (Map<String, Object> view :
                List.of(
                        game.view("ann"),
                        game.view("bob"),
                        game.view("cleo"),
                        game.spectatorView())) {
            Map<String, Object> level = level(view);
            assertThat(level.get("map")).isNotNull();
            assertThat(level.get("window")).isNull();
            assertThat((List<?>) level.get("trail")).hasSizeGreaterThan(5);
        }
    }

    private static int seatIndex(GameHarness game, String playerId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seats =
                (List<Map<String, Object>>) level(game.view(playerId)).get("seats");
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).get("playerId").equals(playerId)) {
                return i;
            }
        }
        return -1;
    }
}
