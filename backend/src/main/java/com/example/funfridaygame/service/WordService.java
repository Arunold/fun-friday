package com.example.funfridaygame.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class WordService {

    private static final List<String> FALLBACK_WORDS =
            List.of(
                    "ADVENTURE",
                    "BUTTERFLY",
                    "CHOCOLATE",
                    "DINOSAUR",
                    "ELEPHANT",
                    "FANTASTIC",
                    "GIRAFFE",
                    "HAMBURGER",
                    "ICECREAM",
                    "JELLYFISH",
                    "KEYBOARD",
                    "LEMONADE",
                    "MOUNTAIN",
                    "NOTEBOOK",
                    "ORCHESTRA",
                    "PINEAPPLE",
                    "QUESTION",
                    "RAINBOW",
                    "SUNSHINE",
                    "TELESCOPE",
                    "UMBRELLA",
                    "VOLCANO",
                    "WATERFALL",
                    "XYLOPHONE",
                    "YESTERDAY",
                    "ZEPPELIN",
                    "AIRPLANE",
                    "BASKETBALL",
                    "COMPUTER",
                    "DOLPHIN",
                    "FIREWORK",
                    "GARDENER",
                    "HOSPITAL",
                    "INTERNET",
                    "JUGGLER",
                    "KANGAROO",
                    "LIGHTNING",
                    "MUSHROOM",
                    "NECKLACE",
                    "OCTOPUS",
                    "PARACHUTE",
                    "QUICKSAND",
                    "REPTILE",
                    "SANDWICH",
                    "TREASURE",
                    "UNIVERSE",
                    "VACATION",
                    "WINDMILL",
                    "EXERCISE",
                    "YOGURT");

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    public String getRandomWord(int charLen) {
        try {
            String[] words =
                    restTemplate.getForObject(
                            "https://random-word-api.herokuapp.com/word?length=" + charLen,
                            String[].class);
            if (words != null && words.length > 0) {
                return words[0].toUpperCase();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch word from API, using fallback: {}", e.getMessage());
        }

        return FALLBACK_WORDS.get(random.nextInt(FALLBACK_WORDS.size()));
    }

    public String scrambleWord(String word) {
        List<String> letters = new java.util.ArrayList<>(Arrays.asList(word.split("")));

        String scrambled;
        int attempts = 0;
        do {
            Collections.shuffle(letters);
            scrambled = String.join("", letters);
            attempts++;
        } while (scrambled.equals(word) && attempts < 10);

        return scrambled;
    }
}
