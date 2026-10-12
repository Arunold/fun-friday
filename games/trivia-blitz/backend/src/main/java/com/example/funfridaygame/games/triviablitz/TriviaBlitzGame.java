package com.example.funfridaygame.games.triviablitz;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/** Trivia Blitz: quick-fire multiple choice — the faster you're right, the more you score. */
@FunFridayGame
public class TriviaBlitzGame implements GameDefinition {

    public static final String ID = "trivia-blitz";
    static final String CATEGORY = "category";
    static final String DIFFICULTY = "difficulty";
    private static final int[] ROUND_CHOICES = {5, 10, 15, 20};

    private final QuestionBank bank;

    /** Plays with the question bank shipped with the game. */
    public TriviaBlitzGame() {
        this(QuestionBank.standard());
    }

    /** Plays with the given bank (tests). */
    TriviaBlitzGame(QuestionBank bank) {
        this.bank = bank;
    }

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Trivia Blitz",
                "Quick-fire multiple choice — the faster you're right, the more you score.",
                "❓",
                2,
                30,
                GameInfo.Difficulty.EASY,
                List.of(
                        "Everyone gets the same question with four answers — your first pick is"
                                + " final.",
                        "Right answers score 1000 points when instant, sliding to 500 at the"
                                + " buzzer.",
                        "Streak bonus: +100 for each right answer in a row after the first (up to"
                                + " +300).",
                        "A wrong or missing answer scores 0 and resets your streak."));
    }

    @Override
    public List<SettingDef> settings() {
        List<SettingDef.Option> categories = new ArrayList<>();
        categories.add(SettingDef.option(QuestionFilter.MIXED, "Mixed"));
        for (Category category : Category.values()) {
            categories.add(
                    SettingDef.option(category.id(), category.icon() + " " + category.label()));
        }
        List<SettingDef.Option> difficulties = new ArrayList<>();
        difficulties.add(SettingDef.option(QuestionFilter.MIXED, "Mixed"));
        for (Difficulty difficulty : Difficulty.values()) {
            difficulties.add(SettingDef.option(difficulty.id(), difficulty.label()));
        }
        return List.of(
                CommonSettings.rounds(10, ROUND_CHOICES),
                CommonSettings.roundDuration(15, 10, 15, 20, 30),
                SettingDef.select(
                        CATEGORY,
                        "Category",
                        QuestionFilter.MIXED,
                        categories.toArray(SettingDef.Option[]::new)),
                SettingDef.select(
                                DIFFICULTY,
                                "Difficulty",
                                QuestionFilter.MIXED,
                                difficulties.toArray(SettingDef.Option[]::new))
                        .withHelp("Mixed starts easy and gets harder."));
    }

    /**
     * Rejects category/difficulty filters with fewer questions than rounds (no question may repeat
     * within a game) and suggests the longest game that still fits.
     */
    @Override
    public List<String> validate(GameSettings settings) {
        QuestionFilter filter = filter(settings);
        int available = bank.count(filter);
        int rounds = settings.rounds();
        if (available >= rounds) {
            return List.of();
        }
        OptionalInt fitting = Arrays.stream(ROUND_CHOICES).filter(r -> r <= available).max();
        String hint =
                fitting.isPresent()
                        ? "play "
                                + fitting.getAsInt()
                                + " rounds or pick a broader category or difficulty"
                        : "pick a broader category or difficulty";
        return List.of(
                "Only " + available + " questions match " + filter.describe() + " — " + hint + ".");
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        QuestionFilter filter = filter(settings);
        return new TriviaBlitzLogic(bank.matching(filter), filter.mixedDifficulty());
    }

    private static QuestionFilter filter(GameSettings settings) {
        return QuestionFilter.of(settings.getString(CATEGORY), settings.getString(DIFFICULTY));
    }
}
