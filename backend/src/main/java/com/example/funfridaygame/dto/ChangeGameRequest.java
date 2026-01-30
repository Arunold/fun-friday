package com.example.funfridaygame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeGameRequest {
    private String gameId;
    private String gameTypeId;

    @Builder.Default private int totalRounds = 3;

    @Builder.Default private int roundDuration = 30;

    @Builder.Default private int wordLength = 7;

    @Builder.Default private String difficulty = "MEDIUM";

    @Builder.Default private int minRange = 1;

    @Builder.Default private int maxRange = 100;

    @Builder.Default private boolean includeFakeOuts = true;
}
