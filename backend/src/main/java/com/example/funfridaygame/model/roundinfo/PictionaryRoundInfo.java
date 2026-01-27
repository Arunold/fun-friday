package com.example.funfridaygame.model.roundinfo;

import com.example.funfridaygame.model.TurnResult;
import com.example.funfridaygame.model.config.PictionaryConfig;
import com.example.funfridaygame.model.round.PictionaryRound;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Round info DTO for Pictionary game.
 */
@Getter
@Setter
@NoArgsConstructor
public class PictionaryRoundInfo extends BaseRoundInfo {
    
    private String drawerName;
    private String wordToDraw;
    private String wordHint;
    private List<Object> drawingData = new ArrayList<>();
    private Map<String, List<String>> playerPictionaryGuesses = new HashMap<>();
    private String correctGuesser;
    private int currentTurnNumber;
    private int totalTurns;
    private List<TurnResult> turnResults = new ArrayList<>();
    
    public PictionaryRoundInfo(PictionaryRound round, boolean includeTarget, PictionaryConfig config) {
        super(round);
        this.drawerName = round.getDrawerName();
        this.drawingData = new ArrayList<>(round.getDrawingData());
        this.playerPictionaryGuesses = new HashMap<>(round.getPlayerPictionaryGuesses());
        this.correctGuesser = round.getCorrectGuesser();
        this.currentTurnNumber = round.getCurrentTurnNumber();
        this.turnResults = new ArrayList<>(round.getTurnResults());
        
        // Get total turns from config if available
        if (config != null) {
            this.totalTurns = config.getTotalTurns();
        }
        
        // Word is only revealed at the end, but always sent for drawer (frontend filters)
        if (includeTarget || round.isRevealed()) {
            this.wordToDraw = round.getWordToDraw();
        } else {
            // Always include word for drawer to see
            this.wordToDraw = round.getWordToDraw();
        }
        
        // Generate hint (underscores for word length)
        if (round.getWordToDraw() != null) {
            this.wordHint = generateWordHint(round.getWordToDraw());
        }
    }
    
    private static String generateWordHint(String word) {
        StringBuilder hint = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) hint.append(" ");
            hint.append("_");
        }
        return hint.toString();
    }
}
