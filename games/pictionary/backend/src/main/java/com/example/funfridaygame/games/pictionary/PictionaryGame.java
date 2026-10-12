package com.example.funfridaygame.games.pictionary;

import static com.example.funfridaygame.sdk.SettingDef.option;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.time.Duration;
import java.util.List;

/** Pictionary: one player draws a secret word, everybody else races to guess it. */
@FunFridayGame
public class PictionaryGame implements GameDefinition {

    public static final String ID = "pictionary";
    static final String DIFFICULTY = "difficulty";
    static final String HINTS = "hints";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Pictionary",
                "One player draws, everyone else races to guess the word.",
                "🎨",
                3,
                10,
                GameInfo.Difficulty.MEDIUM,
                List.of(
                        "Everyone draws once per round: pick one of three words and sketch it"
                                + " — no letters or numbers!",
                        "Guess by typing: a correct guess earns 100 points, dropping to 40 as time"
                                + " runs out, +20 for the first.",
                        "The drawer earns 25 points for every player who guesses the word (up to"
                                + " 150).",
                        "Nearly there? You'll get a private \"So close!\". Letter hints appear as"
                                + " time runs out."));
    }

    @Override
    public List<SettingDef> settings() {
        return List.of(
                CommonSettings.rounds(2, 1, 2, 3),
                CommonSettings.roundDuration(80, 60, 80, 100, 120),
                SettingDef.select(
                                DIFFICULTY,
                                "Words",
                                WordPicker.Mode.MIXED.key(),
                                option(WordPicker.Mode.EASY.key(), "Easy"),
                                option(WordPicker.Mode.MEDIUM.key(), "Medium"),
                                option(WordPicker.Mode.HARD.key(), "Hard"),
                                option(WordPicker.Mode.MIXED.key(), "Mixed"))
                        .withHelp(
                                "Mixed offers the drawer one easy, one medium and one hard word."),
                SettingDef.toggle(HINTS, "Letter hints", true)
                        .withHelp(
                                "Reveal a few letters at half and at three quarters of the time."));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new PictionaryLogic(
                Duration.ofSeconds(settings.getInt(GameSettings.ROUND_DURATION)),
                new WordPicker(
                        WordList.standard(),
                        WordPicker.Mode.fromSetting(settings.getString(DIFFICULTY))),
                settings.getBoolean(HINTS));
    }
}
