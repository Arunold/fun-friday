package com.example.funfridaygame.games.wordscramble;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalises what a player typed, once, as it arrives (BUG-30): trimmed, upper case, accents
 * removed ({@code É → E}) and everything that isn't a letter A–Z dropped, so {@code " plan-et "}
 * becomes {@code PLANET}.
 */
final class GuessText {

    private GuessText() {}

    /** The normalised guess; empty when nothing letter-like was typed. */
    static String normalize(String raw) {
        String decomposed =
                Normalizer.normalize(raw.strip(), Normalizer.Form.NFD).toUpperCase(Locale.ROOT);
        StringBuilder letters = new StringBuilder(decomposed.length());
        for (char c : decomposed.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.append(c);
            }
        }
        return letters.toString();
    }
}
