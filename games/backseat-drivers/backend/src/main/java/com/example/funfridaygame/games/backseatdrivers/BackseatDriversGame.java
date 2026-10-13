package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.sdk.SettingDef.option;

import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.Arrays;
import java.util.List;

/**
 * Backseat Drivers: the whole team drives one office shuttle — everyone controls just one thing and
 * only the navigator sees the map. A real-time co-op game (20 ticks per second).
 */
@FunFridayGame
public class BackseatDriversGame implements GameDefinition {

    public static final String ID = "backseat-drivers";
    static final String SHUFFLE = "shuffle";
    static final String VARIATION = "variation";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Backseat Drivers",
                "One bus. Eight drivers. One button each. Everyone must be able to talk — same"
                        + " room or a call.",
                "🚌",
                3,
                10,
                GameInfo.Difficulty.HARD,
                List.of(
                        "The whole team drives one bus and shares one score — keep talking!",
                        "The Navigator 🧭 sees the map and shouts directions.",
                        "Everyone else controls one thing: steer, gas, brake, horn, wipers or"
                                + " headlights.",
                        "SEAT SHUFFLE every 30 s: everybody gets a new job, the Navigator too.",
                        "Reach the office parking lot before time runs out: ☕ +5 s, 🚧 −5 s —"
                                + " and don't touch the CEO's car!",
                        "Level score: 10 per second left + 50 per coffee − 25 per cone."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                SettingDef.selectInt(GameSettings.ROUNDS, "Levels", 2, null, 1, 2, 3)
                        .withHelp("1 Campus Loop · 2 Downtown · 3 Business Park"),
                SettingDef.selectInt(
                        GameSettings.ROUND_DURATION, "Level timer", 120, " s", 90, 120, 150, 180),
                SettingDef.selectInt(SHUFFLE, "Seat shuffle", 30, " s", 20, 30, 45)
                        .withHelp(
                                "Everyone gets new controls this often — a siren warns 2 s before."),
                SettingDef.select(
                                VARIATION,
                                "Variation",
                                Variation.CLASSIC.key(),
                                Arrays.stream(Variation.values())
                                        .map(v -> option(v.key(), v.label()))
                                        .toArray(SettingDef.Option[]::new))
                        .withHelp(
                                "Rush hour: faster and faster, weaker brakes. Silent navigator: no"
                                        + " talking, only signals."));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new BackseatDriversLogic(Rules.from(settings), TrackLoader.campaign());
    }
}
