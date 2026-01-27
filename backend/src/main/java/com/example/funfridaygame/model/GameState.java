package com.example.funfridaygame.model;

public enum GameState {
    LOBBY,
    STARTING,
    GUESSING,
    DRAWING,      // Pictionary: drawer is actively drawing
    ROUND_RESULT,
    FINISHED
}
