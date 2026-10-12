package com.example.funfridaygame.games.memorymatch;

import com.example.funfridaygame.sdk.CommonSettings;
import com.example.funfridaygame.sdk.FunFridayGame;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Memory Match: take turns flipping cards on one shared board; a match means you go again. */
@FunFridayGame
public class MemoryMatchGame implements GameDefinition {

    public static final String ID = "memory-match";
    static final String BOARD = "board";
    static final String MOVE_TIME = "moveTime";
    static final String THEME = "theme";

    @Override
    public GameInfo info() {
        return new GameInfo(
                ID,
                "Memory Match",
                "Take turns flipping cards on one shared board — every match means you go again.",
                "🃏",
                2,
                10,
                GameInfo.Difficulty.EASY,
                List.of(
                        "Everyone plays on the same board of face-down cards, taking turns.",
                        "On your move flip two cards: a pair stays up in your colour and you go"
                                + " again.",
                        "No pair? Both cards flip back and the next player moves — remember them!",
                        "A pair scores 100, every extra pair in a row +50, the most pairs in a"
                                + " round +100."));
    }

    @Override
    public List<SettingDef> settings() {
        SettingDef.Option[] boards =
                Arrays.stream(BoardSize.values())
                        .map(size -> SettingDef.option(size.key(), size.label()))
                        .toArray(SettingDef.Option[]::new);
        SettingDef.Option[] themes =
                Arrays.stream(CardTheme.values())
                        .map(theme -> SettingDef.option(theme.key(), theme.label()))
                        .toArray(SettingDef.Option[]::new);
        return List.of(
                CommonSettings.rounds(1, 1, 2, 3),
                SettingDef.select(BOARD, "Board", BoardSize.LARGE.key(), boards)
                        .withHelp("8, 10, 12 or 18 pairs. Every round is a new board."),
                SettingDef.selectInt(MOVE_TIME, "Time per move", 10, " s", 5, 10, 15)
                        .withHelp("Flip both cards in time — otherwise the move is skipped."),
                SettingDef.select(THEME, "Cards", CardTheme.MIXED.key(), themes));
    }

    @Override
    public GameLogic createLogic(GameSettings settings) {
        return new MemoryMatchLogic(
                BoardSize.fromKey(settings.getString(BOARD)),
                Duration.ofSeconds(settings.getInt(MOVE_TIME)),
                CardTheme.fromKey(settings.getString(THEME)));
    }
}
