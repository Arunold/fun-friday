package com.example.funfridaygame.sdk;

/**
 * A player as seen by game logic.
 *
 * @param id stable, opaque player id (use it as map key, never the name)
 * @param name display name, unique within the room
 * @param avatar an emoji
 * @param color the player's colour as a CSS hex value
 */
public record PlayerInfo(String id, String name, String avatar, String color) {}
