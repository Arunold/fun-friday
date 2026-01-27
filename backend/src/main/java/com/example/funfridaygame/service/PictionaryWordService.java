package com.example.funfridaygame.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class PictionaryWordService {

    private static final List<String> WORDS = List.of(
        // Animals
        "cat", "dog", "fish", "bird", "snake", "elephant", "lion", "monkey", 
        "rabbit", "turtle", "butterfly", "spider", "penguin", "whale", "shark",
        
        // Objects
        "house", "car", "tree", "sun", "moon", "star", "book", "phone",
        "chair", "table", "lamp", "clock", "door", "window", "bed",
        "pizza", "cake", "apple", "banana", "hamburger", "ice cream",
        
        // Actions/Things
        "rain", "fire", "snow", "flower", "mountain", "beach", "ocean",
        "airplane", "boat", "train", "bicycle", "rocket", "balloon",
        
        // Body parts
        "eye", "hand", "heart", "smile", "ear", "nose",
        
        // Simple concepts
        "rainbow", "cloud", "lightning", "bridge", "castle", "crown",
        "guitar", "drum", "piano", "camera", "glasses", "umbrella",
        
        // Food
        "egg", "cheese", "cookie", "donut", "sandwich", "hotdog",
        "watermelon", "grapes", "carrot", "broccoli",
        
        // Sports/Games
        "football", "basketball", "tennis", "soccer", "swimming",
        
        // Nature
        "leaf", "mushroom", "volcano", "island", "river", "waterfall"
    );

    private final Random random = new Random();

    public String getRandomWord() {
        return WORDS.get(random.nextInt(WORDS.size()));
    }

    public String getRandomWord(int maxLength) {
        List<String> filtered = WORDS.stream()
            .filter(w -> w.length() <= maxLength)
            .toList();
        return filtered.isEmpty() ? getRandomWord() : filtered.get(random.nextInt(filtered.size()));
    }
}
