package com.example.funfridaygame.history;

import org.springframework.data.relational.core.mapping.Table;

/**
 * A player's result in a {@link GameRecord}.
 *
 * @param playerName display name at the time
 * @param avatar emoji
 * @param score final score
 * @param placement rank (ties share a rank)
 * @param cupPoints Friday Cup points earned
 */
@Table("GAME_RECORD_PLAYER")
public record GameRecordPlayer(
        String playerName, String avatar, int score, int placement, int cupPoints) {}
