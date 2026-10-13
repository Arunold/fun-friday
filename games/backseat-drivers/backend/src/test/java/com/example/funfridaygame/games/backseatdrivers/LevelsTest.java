package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.level;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.navigator;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The three curated levels, played through the engine: their size and contents as the navigator
 * sees them, the CEO's car parked right behind the bus, and that a decent team (the {@link
 * Autopilot}) parks on each of them in time. Broken map files are refused when a game starts.
 */
class LevelsTest {

    /** A game of the real Backseat Drivers definition (the shipped campaign). */
    private static GameHarness campaign(int levels, String... players) {
        return GameHarness.of(new BackseatDriversGame())
                .players(players)
                .settings(Map.of("rounds", levels))
                .seed(3)
                .start();
    }

    /** A one-level game on shipped level {@code n}. */
    private static GameHarness shipped(int n) {
        return TestGames.start(
                List.of(TrackLoader.campaign().get(n - 1)), Map.of(), "ann", "bob", "cleo");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> navigatorMap(GameHarness game) {
        return (Map<String, Object>) level(game.view(navigator(game))).get("map");
    }

    @SuppressWarnings("unchecked")
    private static String terrain(GameHarness game) {
        return String.join("\n", (List<String>) navigatorMap(game).get("rows"));
    }

    @Test
    void roundNDrivesLevelNInAFixedOrder() {
        GameHarness game = campaign(3, "ann", "bob", "cleo");
        assertThat(level(game.view("ann")).get("name")).isEqualTo("Campus Loop");
        press(game, Control.BRAKE, true).advanceSeconds(2).nextTurn();
        assertThat(level(game.view("ann")).get("name")).isEqualTo("Downtown");
        press(game, Control.BRAKE, true).advanceSeconds(2).nextTurn();
        assertThat(level(game.view("ann")).get("name")).isEqualTo("Business Park");
        assertThat(level(game.view("ann")).get("number")).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void everyLevelIsALargeCuratedMap(int n) {
        Map<String, Object> map = navigatorMap(shipped(n));
        assertThat((Integer) map.get("w")).isBetween(60, 80);
        assertThat((Integer) map.get("h")).isBetween(40, 50);
        assertThat((List<?>) map.get("cones")).hasSizeGreaterThanOrEqualTo(8);
        assertThat((List<?>) map.get("coffees")).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void theLevelsAddNewHazards() {
        GameHarness campus = shipped(1);
        assertThat(terrain(campus)).doesNotContain("~", "t");
        assertThat((List<?>) navigatorMap(campus).get("geese")).isNotEmpty();

        GameHarness downtown = shipped(2);
        assertThat(terrain(downtown)).contains("~", "t");

        GameHarness park = shipped(3);
        assertThat(terrain(park)).contains("~", "t");
        assertThat((List<?>) navigatorMap(park).get("geese")).hasSizeGreaterThanOrEqualTo(2);
        assertThat((List<?>) park.debugState().get("ceoCars"))
                .as("two cars of two cells: one behind the bus, one by the parking lot")
                .hasSize(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void everyLevelStartsWithTheCeosCarRightBehindTheBus(int n) {
        GameHarness game = shipped(n);
        press(game, Control.BRAKE, true).advanceSeconds(0.6);
        assertThat(game.phase()).as("a short reverse is fine").isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.8);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("The CEO's car! Level over.");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void aDecentTeamParksOnEveryLevelInTime(int n) {
        GameHarness game = shipped(n);
        double seconds = new Autopilot(game).drive(120);

        assertThat(game.phase())
                .as("level " + n + " after " + seconds + " s")
                .isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).startsWith("Parked with");
        assertThat(seconds).as("level " + n + " takes a while").isBetween(35.0, 90.0);
        assertThat((Integer) game.debugState().get("rolesVersion"))
                .as("at least one SEAT SHUFFLE on the way")
                .isGreaterThan(1);
    }

    @Test
    void aDefaultGamePlaysTwoLevelsAndTheVote() {
        GameHarness game = campaign(2, "ann", "bob", "cleo", "dan");
        new Autopilot(game).drive(120);
        assertThat(game.lastTurn().headline()).startsWith("Parked with");
        game.nextTurn();
        new Autopilot(game).drive(120);
        assertThat(game.lastTurn().headline()).startsWith("Parked with");
        game.nextTurn();
        assertThat(game.view("ann").get("mode")).isEqualTo("VOTE");
        game.finishGame();
        assertThat(game.result().standings()).extracting(s -> s.rank()).containsOnly(1);
        assertThat(game.score("ann")).isGreaterThan(400);
    }

    // ------------------------------------------------------------------ broken maps

    private static GameHarness startWith(String mapText) {
        GameDefinition definition =
                new BackseatDriversGame() {
                    @Override
                    public GameLogic createLogic(GameSettings settings) {
                        return new BackseatDriversLogic(
                                Rules.from(settings),
                                List.of(TrackLoader.parse("custom.txt", mapText)));
                    }
                };
        return GameHarness.of(definition)
                .players("ann", "bob", "cleo")
                .settings(Map.of("rounds", 1))
                .start();
    }

    private static String file(String heading, String... rows) {
        return "name: Custom\nheading: " + heading + "\n---\n" + String.join("\n", rows) + "\n";
    }

    @Test
    void aValidCustomMapStarts() {
        GameHarness game =
                startWith(
                        file("W", "#########", "#PrCrKSE#", "#.rGG~~T#", "#w.Gtt.##", "#########"));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        assertThat(navigatorMap(game).get("rows"))
                .as("start and hazards stand on road, taking on the rain or tunnel next to them")
                .isEqualTo(
                        List.of("#########", "#Prrr~~E#", "#.rr~~~T#", "#w.ttt.##", "#########"));
        assertThat((List<?>) navigatorMap(game).get("geese")).hasSize(1);
    }

    @Test
    void brokenMapsAreRefusedWithAClearMessage() {
        assertThatThrownBy(() -> startWith("name: X\nheading: E\n#ESP#"))
                .hasMessageContaining("---");
        assertThatThrownBy(() -> startWith("heading: E\n---\n#ESP#")).hasMessageContaining("name");
        assertThatThrownBy(() -> startWith("name: X\n---\n#ESP#")).hasMessageContaining("heading");
        assertThatThrownBy(() -> startWith(file("Q", "#ESP#")))
                .hasMessageContaining("N, E, S or W");
        assertThatThrownBy(() -> startWith(file("E", "#ESP#", "####")))
                .hasMessageContaining("row 2 has 4 columns, expected 5");
        assertThatThrownBy(() -> startWith(file("E", "#ESx#")))
                .hasMessageContaining("unknown character 'x' at column 4, row 1");
        assertThatThrownBy(() -> startWith(file("E", "#ErP#"))).hasMessageContaining("found 0");
        assertThatThrownBy(() -> startWith(file("E", "#ESSP#"))).hasMessageContaining("found 2");
        assertThatThrownBy(() -> startWith(file("E", "#ESrr#")))
                .hasMessageContaining("parking tile");
        assertThatThrownBy(() -> startWith(file("E", "#rSrP#"))).hasMessageContaining("CEO's car");
        assertThatThrownBy(() -> startWith(file("E", "#ES.P#")))
                .as("grass is not road")
                .hasMessageContaining("can't be reached by road");
        assertThatThrownBy(() -> startWith("name: X\nheading: E\n---\n"))
                .hasMessageContaining("no rows");
    }
}
