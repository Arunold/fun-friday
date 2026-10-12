package com.example.funfridaygame.games.speedtyping;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/** Speed Typing: everyone races to type the same text, fast and without typos. */
@FunFridayGame
public class SpeedTypingGame implements GameDefinition {

    public static final String ID = "speed-typing";
    static final String DIFFICULTY = "difficulty";
    static final String ROUND_TIME = "roundTime";
    static final String MIN_ACCURACY = "minAccuracy";

    /** Shortest round timer that leaves a fair chance to finish a long text. */
    private static final int MIN_SECONDS_FOR_LONG = 60;

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Speed Typing",
                "Race your team to type the same text, fast and without typos.",
                "⌨️",
                2,
                20,
                GameInfo.Difficulty.MEDIUM,
                List.of(
                        "Everyone gets the same text at the same moment: type it exactly.",
                        "Typos turn red; fix them, the text must match to finish.",
                        "Finishers score 100/80/65/55/50 by place plus WPM ÷ 4 (max 30).",
                        "Points are multiplied by your accuracy, halved below the minimum.",
                        "Not finished? Up to 40 points for the correctly typed part."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(3, 1, 3, 5),
                SettingDef.select(
                                DIFFICULTY,
                                "Text length",
                                TextLength.MEDIUM.name(),
                                option(TextLength.SHORT),
                                option(TextLength.MEDIUM),
                                option(TextLength.LONG))
                        .withHelp("Short: one sentence · Medium: one or two · Long: a passage"),
                SettingDef.select(
                                ROUND_TIME,
                                "Round timer",
                                0,
                                SettingDef.option(0, "Auto"),
                                SettingDef.option(30, "30 s"),
                                SettingDef.option(60, "60 s"),
                                SettingDef.option(90, "90 s"),
                                SettingDef.option(120, "120 s"))
                        .withHelp(autoHelp()),
                SettingDef.selectInt(MIN_ACCURACY, "Minimum accuracy", 90, "%", 80, 90, 95)
                        .withHelp("Finishing below it halves your points."));
    }

    @Override
    public List<String> validate(GameSettings settings) {
        int seconds = settings.getInt(ROUND_TIME);
        if (settings.getEnum(DIFFICULTY, TextLength.class) == TextLength.LONG
                && seconds > 0
                && seconds < MIN_SECONDS_FOR_LONG) {
            return List.of(
                    "Long texts need a round timer of at least "
                            + MIN_SECONDS_FOR_LONG
                            + " s (or Auto).");
        }
        return List.of();
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        RaceConfig config =
                new RaceConfig(
                        settings.getEnum(DIFFICULTY, TextLength.class),
                        settings.getInt(ROUND_TIME),
                        settings.getInt(MIN_ACCURACY));
        return new SpeedTypingLogic(config, TextLibrary.standard());
    }

    private static SettingDef.Option option(TextLength length) {
        return SettingDef.option(length.name(), length.label());
    }

    /** "Auto: 45 s short, 75 s medium, 120 s long." */
    private static String autoHelp() {
        return "Auto: "
                + TextLength.SHORT.autoSeconds()
                + " s short, "
                + TextLength.MEDIUM.autoSeconds()
                + " s medium, "
                + TextLength.LONG.autoSeconds()
                + " s long.";
    }
}
