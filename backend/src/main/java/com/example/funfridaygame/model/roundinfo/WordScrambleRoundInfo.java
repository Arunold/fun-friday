package com.example.funfridaygame.model.roundinfo;

import com.example.funfridaygame.model.round.WordScrambleRound;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Round info DTO for Word Scramble game. */
@Getter
@Setter
@NoArgsConstructor
public class WordScrambleRoundInfo extends BaseRoundInfo {

    private String scrambledWord;
    private String originalWord;
    private Map<String, String> playerWordGuesses = new HashMap<>();

    public WordScrambleRoundInfo(WordScrambleRound round, boolean includeTarget) {
        super(round);
        this.scrambledWord = round.getScrambledWord();
        this.playerWordGuesses = new HashMap<>(round.getPlayerWordGuesses());

        if (includeTarget || round.isRevealed()) {
            this.originalWord = round.getOriginalWord();
        }
    }
}
