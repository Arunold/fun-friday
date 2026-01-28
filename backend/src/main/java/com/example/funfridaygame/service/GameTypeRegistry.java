package com.example.funfridaygame.service;

import com.example.funfridaygame.model.GameType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GameTypeRegistry {

    private final List<GameType> gameTypes = new ArrayList<>();

    public GameTypeRegistry() {
        registerGameTypes();
    }

    private void registerGameTypes() {
        gameTypes.add(
                GameType.builder()
                        .id("number-guess")
                        .name("Number Guess")
                        .description(
                                "Guess the secret number! Players try to guess a random number between 1-100. Closest guess wins!")
                        .icon("🔢")
                        .minPlayers(2)
                        .maxPlayers(10)
                        .build());

        gameTypes.add(
                GameType.builder()
                        .id("word-scramble")
                        .name("Word Scramble")
                        .description(
                                "Unscramble the letters! Race to figure out the hidden word. Fastest correct answer wins!")
                        .icon("🔤")
                        .minPlayers(2)
                        .maxPlayers(10)
                        .build());

        gameTypes.add(
                GameType.builder()
                        .id("pictionary")
                        .name("Pictionary")
                        .description(
                                "Draw and guess! One player draws while others try to guess the word. Take turns being the artist!")
                        .icon("🎨")
                        .minPlayers(2)
                        .maxPlayers(10)
                        .build());

        gameTypes.add(
                GameType.builder()
                        .id("speed-typing")
                        .name("Speed Typing Race")
                        .description(
                                "Race to type the displayed sentence! Fastest accurate typer wins. Test your typing speed and accuracy!")
                        .icon("⌨️")
                        .minPlayers(2)
                        .maxPlayers(10)
                        .build());

        gameTypes.add(
                GameType.builder()
                        .id("reaction-showdown")
                        .name("Reaction Showdown")
                        .description(
                                "Test your reflexes! Tap when you see the green signal. Watch out for fake-outs!")
                        .icon("⚡")
                        .minPlayers(2)
                        .maxPlayers(10)
                        .build());
    }

    public List<GameType> getAllGameTypes() {
        return List.copyOf(gameTypes);
    }

    public Optional<GameType> getGameTypeById(String id) {
        return gameTypes.stream().filter(gt -> gt.getId().equals(id)).findFirst();
    }
}
