package com.example.funfridaygame.games.numberguess;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/** Number Guess: find the secret number with higher/lower hints. */
@FunFridayGame
public class NumberGuessGame implements GameDefinition {

    public static final String ID = "number-guess";
    static final String MIN = "minRange";
    static final String MAX = "maxRange";
    static final String ATTEMPTS = "attempts";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Number Guess",
                "Find the secret number — every guess tells you higher or lower.",
                "🔢",
                2,
                16,
                GameInfo.Difficulty.EASY,
                List.of(
                        "A secret number is picked every round.",
                        "You get a few guesses; each one answers \"higher\" or \"lower\".",
                        "Exact hits score most — fewer guesses and a fast answer score more.",
                        "No hit? The closer your best guess, the more points."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(3, 1, 2, 3, 5, 7, 10),
                CommonSettings.roundDuration(30, 15, 20, 30, 45, 60),
                SettingDef.selectInt(MIN, "Lowest number", 1, null, 1, 10, 50, 100),
                SettingDef.selectInt(MAX, "Highest number", 100, null, 50, 100, 500, 1000),
                SettingDef.selectInt(ATTEMPTS, "Guesses per round", 3, null, 1, 3, 5)
                        .withHelp("With one guess it's a pure closest-guess-wins game."));
    }

    @Override
    public List<String> validate(GameSettings settings) {
        if (settings.getInt(MAX) - settings.getInt(MIN) < 9) {
            return List.of("The highest number must be at least 10 above the lowest number.");
        }
        return List.of();
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new NumberGuessLogic(
                settings.getInt(MIN), settings.getInt(MAX), settings.getInt(ATTEMPTS));
    }
}
