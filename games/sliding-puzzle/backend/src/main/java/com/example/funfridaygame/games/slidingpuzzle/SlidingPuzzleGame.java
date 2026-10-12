package com.example.funfridaygame.games.slidingpuzzle;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/** Sliding Puzzle: everyone races to solve the same scrambled number puzzle. */
@FunFridayGame
public class SlidingPuzzleGame implements GameDefinition {

    public static final String ID = "sliding-puzzle";
    static final String SIZE = "size";
    static final String TIME_LIMIT = "timeLimit";

    /** {@value #TIME_LIMIT} value that picks the length from the board size. */
    static final int AUTO = 0;

    /** The shortest time limit a 5×5 board may get. */
    static final int MIN_SECONDS_5X5 = 120;

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Sliding Puzzle",
                "Everyone gets the same scrambled puzzle — slide the tiles home first.",
                "🧩",
                2,
                20,
                GameInfo.Difficulty.HARD,
                List.of(
                        "Everyone gets the same scramble: put the tiles back in order, gap bottom-right.",
                        "Click a tile in the gap's row or column, or use the arrow keys or WASD.",
                        "Solvers score by finishing order (100, 80, 65, 55, 50) plus up to 30 for few moves.",
                        "Not solved in time? Up to 40 points for the tiles already in place."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(2, 1, 2, 3),
                SettingDef.select(
                        SIZE,
                        "Board size",
                        4,
                        SettingDef.option(3, "3×3"),
                        SettingDef.option(4, "4×4"),
                        SettingDef.option(5, "5×5")),
                SettingDef.select(
                                TIME_LIMIT,
                                "Time limit",
                                AUTO,
                                SettingDef.option(AUTO, "Auto"),
                                SettingDef.option(60, "1 min"),
                                SettingDef.option(120, "2 min"),
                                SettingDef.option(180, "3 min"),
                                SettingDef.option(240, "4 min"),
                                SettingDef.option(300, "5 min"))
                        .withHelp("Auto: 1 min for 3×3, 2½ min for 4×4, 5 min for 5×5."));
    }

    @Override
    public List<String> validate(GameSettings settings) {
        int chosen = settings.getInt(TIME_LIMIT);
        if (settings.getInt(SIZE) == 5 && chosen != AUTO && chosen < MIN_SECONDS_5X5) {
            return List.of("A 5×5 puzzle needs at least 2 minutes — pick Auto or a longer limit.");
        }
        return List.of();
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        int size = settings.getInt(SIZE);
        return new SlidingPuzzleLogic(size, timeLimitSeconds(size, settings.getInt(TIME_LIMIT)));
    }

    /**
     * Length of a round (BUG-05: it depends on the board size): the chosen limit, or for "Auto" 60
     * s on 3×3, 150 s on 4×4 and 300 s on 5×5.
     */
    static int timeLimitSeconds(int size, int chosen) {
        if (chosen != AUTO) {
            return chosen;
        }
        return switch (size) {
            case 3 -> 60;
            case 4 -> 150;
            default -> 300;
        };
    }
}
