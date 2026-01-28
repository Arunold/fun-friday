package com.example.funfridaygame.model.roundinfo;

import com.example.funfridaygame.model.round.NumberGuessRound;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Round info DTO for Number Guess game. */
@Getter
@Setter
@NoArgsConstructor
public class NumberGuessRoundInfo extends BaseRoundInfo {

    private int minRange;
    private int maxRange;
    private Map<String, Integer> playerGuesses = new HashMap<>();
    private Integer targetNumber;

    public NumberGuessRoundInfo(NumberGuessRound round, boolean includeTarget) {
        super(round);
        this.minRange = round.getMinRange();
        this.maxRange = round.getMaxRange();
        this.playerGuesses = new HashMap<>(round.getPlayerGuesses());

        if (includeTarget || round.isRevealed()) {
            this.targetNumber = round.getTargetNumber();
        }
    }
}
