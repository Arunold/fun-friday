package com.example.funfridaygame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for reaction tap in Reaction Showdown game.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionTapRequest {
    private String gameId;
    private String playerName;
    private long tapTime; // Client timestamp when player tapped
}
