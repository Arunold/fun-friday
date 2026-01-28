package com.example.funfridaygame.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request DTO for puzzle move actions. */
@Getter
@Setter
@NoArgsConstructor
public class PuzzleMoveRequest {
    private String gameId;
    private String playerName;
    private int tileIndex; // Index of tile to move (0 to gridSize*gridSize - 1)
}
