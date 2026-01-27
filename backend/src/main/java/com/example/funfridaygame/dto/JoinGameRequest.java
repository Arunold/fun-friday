package com.example.funfridaygame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinGameRequest {
    private String playerName;
    private String gameCode;
    private String avatar;
}
