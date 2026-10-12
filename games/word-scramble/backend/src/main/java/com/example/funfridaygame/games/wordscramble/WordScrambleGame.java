package com.example.funfridaygame.games.wordscramble;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.List;

/**
 * Word Scramble: everyone unscrambles the same word; the faster you crack it, the more you score.
 */
@FunFridayGame
public class WordScrambleGame implements GameDefinition {

    public static final String ID = "word-scramble";
    static final String WORD_LENGTH = "wordLength";
    static final String HINTS = "hints";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Word Scramble",
                "Unscramble the word before everyone else — every second counts.",
                "🔤",
                2,
                20,
                GameInfo.Difficulty.MEDIUM,
                List.of(
                        "Everyone gets the same scrambled letters — type the word they spell.",
                        "Guess as often as you like; only you see your wrong guesses.",
                        "First solver scores 100, then 80, 65 and 50 — plus up to 20 for speed.",
                        "Stuck? A hint reveals a letter but costs 25 % of your round points.",
                        "The round ends when everyone has solved it or time runs out."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(5, 3, 5, 7, 10),
                CommonSettings.roundDuration(45, 30, 45, 60, 90),
                SettingDef.select(
                                WORD_LENGTH,
                                "Word length",
                                "6",
                                SettingDef.option("4", "4"),
                                SettingDef.option("5", "5"),
                                SettingDef.option("6", "6"),
                                SettingDef.option("7", "7"),
                                SettingDef.option("8", "8"),
                                SettingDef.option(WordLength.MIXED, "Mixed"))
                        .withHelp("Letters per word. Mixed: 5 to 7, different every round."),
                SettingDef.toggle(HINTS, "Hints", true)
                        .withHelp(
                                "Players may reveal up to 2 letters, each costs 25 % of the round."));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new WordScrambleLogic(
                WordLength.parse(settings.getString(WORD_LENGTH)), settings.getBoolean(HINTS));
    }
}
