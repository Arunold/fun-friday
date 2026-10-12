package com.example.funfridaygame.games.reactionshowdown;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/**
 * Reaction Showdown: wait for green, then tap faster than everyone — without falling for the
 * fake-outs.
 *
 * <p>There is deliberately no round timer setting: the logic schedules its own signals and sets the
 * turn deadline itself (BUG-05).
 */
@FunFridayGame
public class ReactionShowdownGame implements GameDefinition {

    public static final String ID = "reaction-showdown";
    static final String SIGNALS = "signals";
    static final String FAKEOUTS = "fakeouts";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Reaction Showdown",
                "Wait for green, then tap faster than everyone — but don't fall for the fake-outs!",
                "⚡",
                2,
                20,
                GameInfo.Difficulty.EASY,
                List.of(
                        "Every round has a few signals: wait while the field is dark…",
                        "…and tap (or press Space) the moment it turns green.",
                        "The fastest tap scores 100, then 80, 65, 50, 40, 30…",
                        "Tapping too soon — or on a fake-out colour — is a false start: −25."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(3, 1, 3, 5),
                SettingDef.selectInt(SIGNALS, "Signals per round", 3, null, 3, 5),
                SettingDef.toggle(FAKEOUTS, "Fake-outs", true)
                        .withHelp("A decoy colour sometimes flashes before green."));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new ReactionShowdownLogic(settings.getInt(SIGNALS), settings.getBoolean(FAKEOUTS));
    }
}
