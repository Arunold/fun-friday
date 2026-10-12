package com.example.funfridaygame.sdk;

/**
 * A fun title shown on the podium.
 *
 * @param emoji decoration, e.g. "⚡"
 * @param title e.g. "Fastest fingers"
 * @param playerId who earned it
 * @param detail e.g. "182 ms"
 */
public record Award(String emoji, String title, String playerId, String detail) {}
