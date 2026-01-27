package com.example.funfridaygame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for typing progress updates in Speed Typing Race game.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingProgressRequest {
    private String gameId;
    private String playerName;
    private String typedText;
}
