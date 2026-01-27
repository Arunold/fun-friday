package com.example.funfridaygame.model.roundinfo;

import com.example.funfridaygame.model.config.BaseGameConfig;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.round.BaseGameRound;
import com.example.funfridaygame.model.round.NumberGuessRound;
import com.example.funfridaygame.model.round.PictionaryRound;
import com.example.funfridaygame.model.round.ReactionShowdownRound;
import com.example.funfridaygame.model.round.SpeedTypingRound;
import com.example.funfridaygame.model.round.WordScrambleRound;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base DTO for sending round info to clients.
 * Each game type extends this with its own fields.
 * Uses Jackson polymorphism for proper JSON serialization.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "gameType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NumberGuessRoundInfo.class, name = "number-guess"),
    @JsonSubTypes.Type(value = WordScrambleRoundInfo.class, name = "word-scramble"),
    @JsonSubTypes.Type(value = PictionaryRoundInfo.class, name = "pictionary"),
    @JsonSubTypes.Type(value = SpeedTypingRoundInfo.class, name = "speed-typing"),
    @JsonSubTypes.Type(value = ReactionShowdownRoundInfo.class, name = "reaction-showdown")
})
public abstract class BaseRoundInfo {

    protected int roundNumber;
    protected String winner;
    protected boolean revealed;

    protected BaseRoundInfo(BaseGameRound round) {
        this.roundNumber = round.getRoundNumber();
        this.winner = round.getWinner();
        this.revealed = round.isRevealed();
    }

    /**
     * Factory method to create the appropriate RoundInfo based on round type
     */
    public static BaseRoundInfo fromRound(BaseGameRound round, boolean includeTarget, BaseGameConfig config) {
        if (round == null) {
            return null;
        }
        if (round instanceof NumberGuessRound ngRound) {
            return new NumberGuessRoundInfo(ngRound, includeTarget);
        } else if (round instanceof WordScrambleRound wsRound) {
            return new WordScrambleRoundInfo(wsRound, includeTarget);
        } else if (round instanceof PictionaryRound pRound) {
            PictionaryConfig pConfig = config instanceof PictionaryConfig ? (PictionaryConfig) config : null;
            return new PictionaryRoundInfo(pRound, includeTarget, pConfig);
        } else if (round instanceof SpeedTypingRound stRound) {
            return new SpeedTypingRoundInfo(stRound, includeTarget);
        } else if (round instanceof ReactionShowdownRound rsRound) {
            return new ReactionShowdownRoundInfo(rsRound);
        }

        throw new IllegalArgumentException("Unknown round type: " + round.getClass().getName());
    }
}
