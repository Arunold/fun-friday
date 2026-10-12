package com.example.funfridaygame.runtime;

import com.example.funfridaygame.sdk.GameLogic;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.time.InstantSource;
import java.util.List;
import java.util.random.RandomGenerator;

/** Collects the collaborators of a {@link GameRun}; obtain one with {@link GameRun#builder()}. */
public final class GameRunBuilder {
    String roomCode;
    String gameId;
    GameSettings settings;
    GameLogic logic;
    List<PlayerInfo> players = List.of();
    RandomGenerator random;
    InstantSource clock = InstantSource.system();
    TimerService timers;
    ActionConverter converter;
    GameRunConfig config = GameRunConfig.DEFAULT;
    GameRunListener listener;

    GameRunBuilder() {}

    public GameRunBuilder roomCode(String value) {
        this.roomCode = value;
        return this;
    }

    public GameRunBuilder gameId(String value) {
        this.gameId = value;
        return this;
    }

    public GameRunBuilder settings(GameSettings value) {
        this.settings = value;
        return this;
    }

    public GameRunBuilder logic(GameLogic value) {
        this.logic = value;
        return this;
    }

    public GameRunBuilder players(List<PlayerInfo> value) {
        this.players = List.copyOf(value);
        return this;
    }

    public GameRunBuilder random(RandomGenerator value) {
        this.random = value;
        return this;
    }

    public GameRunBuilder clock(InstantSource value) {
        this.clock = value;
        return this;
    }

    public GameRunBuilder timers(TimerService value) {
        this.timers = value;
        return this;
    }

    public GameRunBuilder converter(ActionConverter value) {
        this.converter = value;
        return this;
    }

    public GameRunBuilder config(GameRunConfig value) {
        this.config = value;
        return this;
    }

    public GameRunBuilder listener(GameRunListener value) {
        this.listener = value;
        return this;
    }

    public GameRun build() {
        return new GameRun(this);
    }
}
