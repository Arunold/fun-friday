package com.example.funfridaygame.sdk;

/** Settings most games share, so labels and keys stay consistent across games. */
public final class CommonSettings {

    private CommonSettings() {}

    /**
     * Number of rounds in a game.
     *
     * @param defaultRounds pre-selected value; must be one of {@code choices}
     * @param choices the values offered to the host
     */
    public static SettingDef rounds(int defaultRounds, int... choices) {
        return SettingDef.selectInt(GameSettings.ROUNDS, "Rounds", defaultRounds, null, choices);
    }

    /**
     * Length of a turn in seconds. The platform uses it as the default turn deadline.
     *
     * @param defaultSeconds pre-selected value; must be one of {@code choices}
     * @param choices the values offered to the host
     */
    public static SettingDef roundDuration(int defaultSeconds, int... choices) {
        return SettingDef.selectInt(
                GameSettings.ROUND_DURATION, "Round timer", defaultSeconds, " s", choices);
    }
}
