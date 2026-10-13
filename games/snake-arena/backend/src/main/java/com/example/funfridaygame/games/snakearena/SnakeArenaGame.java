package com.example.funfridaygame.games.snakearena;

import static com.example.funfridaygame.sdk.SettingDef.option;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/** Snake Arena: multiplayer Snake on one shared grid — eat, grow and make the others crash. */
@FunFridayGame
public class SnakeArenaGame implements GameDefinition {

    public static final String ID = "snake-arena";
    static final String ARENA = "arena";
    static final String VARIATION = "variation";
    static final String POWER_UPS = "powerUps";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Snake Arena",
                "The classic Snake, except the other snakes are your coworkers.",
                "🐍",
                2,
                12,
                GameInfo.Difficulty.HARD,
                List.of(
                        "Steer with the arrow keys or WASD — your snake never stops and can't"
                                + " reverse.",
                        "🍎 Food: +1 point, and you grow one segment.",
                        "Another snake crashes into your body: +5 points.",
                        "Longest snake when the round ends: +5 points.",
                        "Crash and you respawn after 3 s — you keep your points.",
                        "Power-ups: ⚡ double speed, 👻 pass through snakes, 🧲 pull in food."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(3, 1, 2, 3, 5),
                CommonSettings.roundDuration(90, 60, 90, 120, 180),
                SettingDef.select(
                                ARENA,
                                "Arena",
                                ArenaSize.AUTO,
                                option(ArenaSize.AUTO, "Auto"),
                                option(ArenaSize.SMALL.key(), "Small"),
                                option(ArenaSize.MEDIUM.key(), "Medium"),
                                option(ArenaSize.LARGE.key(), "Large"))
                        .withHelp(
                                "Small 36×24, medium 44×28, large 52×32 cells. Auto: small for up"
                                        + " to 3 players, medium up to 6, large for more."),
                SettingDef.select(
                                VARIATION,
                                "Variation",
                                Variation.CLASSIC.key(),
                                option(Variation.CLASSIC.key(), Variation.CLASSIC.label()),
                                option(Variation.SHRINKING.key(), Variation.SHRINKING.label()),
                                option(
                                        Variation.LAST_STANDING.key(),
                                        Variation.LAST_STANDING.label()))
                        .withHelp(
                                "Shrinking: the walls close in during the last 30 s. Last snake"
                                        + " standing: no respawns, the survivor gets +10."),
                SettingDef.toggle(POWER_UPS, "Power-ups", true)
                        .withHelp("⚡ Boost, 👻 Ghost and 🧲 Magnet appear every 10–15 s."));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new SnakeArenaLogic(
                settings.getString(ARENA),
                Variation.fromKey(settings.getString(VARIATION)),
                settings.getBoolean(POWER_UPS));
    }
}
