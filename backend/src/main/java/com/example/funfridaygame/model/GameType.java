package com.example.funfridaygame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameType {
    private String id;
    private String name;
    private String description;
    private String icon;
    private int minPlayers;
    private int maxPlayers;
}
