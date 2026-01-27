package com.example.funfridaygame.model.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Base configuration for game-specific settings.
 * Each game type extends this with its own configuration fields.
 * This keeps Game.java clean and game-agnostic.
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NumberGuessConfig.class, name = "number-guess"),
    @JsonSubTypes.Type(value = WordScrambleConfig.class, name = "word-scramble"),
    @JsonSubTypes.Type(value = PictionaryConfig.class, name = "pictionary")
})
public abstract class BaseGameConfig {
    
    protected int roundDuration = 30;
    
    /**
     * Called when a new round starts
     */
    public abstract void onRoundStart(int playerCount);
    
    /**
     * Called when a round ends
     */
    public abstract void onRoundEnd();
}
