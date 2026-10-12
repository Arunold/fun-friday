package com.example.funfridaygame.games.__PACKAGE__;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/** __GAME_NAME__: everyone secretly picks a number; whoever is closest to the lucky number scores. */
@FunFridayGame
public class __CLASS__Game implements GameDefinition {

    public static final String ID = "__GAME_ID__";
    static final String HIGHEST = "highest";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "__GAME_NAME__",
                "Pick a number — the closest to the lucky number wins the round.",
                "__GAME_ICON__",
                2,
                12,
                GameInfo.Difficulty.EASY,
                List.of(
                        "Everyone secretly picks a number.",
                        "Then the lucky number is drawn.",
                        "Closest pick: 10 points. An exact hit: 20 points."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(3, 1, 3, 5),
                CommonSettings.roundDuration(15, 10, 15, 30),
                SettingDef.selectInt(HIGHEST, "Highest number", 10, null, 10, 20, 50));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new __CLASS__Logic(settings.getInt(HIGHEST));
    }
}
