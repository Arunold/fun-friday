package com.example.funfridaygame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGameRequest {
    private String hostName;
    private String gameTypeId;
    private String avatar;

    @Builder.Default private int totalRounds = 3;

    @Builder.Default private int wordLength = 7;

    @Builder.Default private int roundDuration = 30;
}
