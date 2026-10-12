package com.example.funfridaygame.sdk;

/** A player action, e.g. {@code {"type": "guess", "payload": {"value": 42}}}. */
public interface Action {

    /** Action name chosen by the game, e.g. {@code "guess"}. */
    String type();

    /**
     * Converts the JSON payload to {@code type} (typically a record) and validates its Bean
     * Validation constraints.
     *
     * @throws InvalidActionException when the payload doesn't fit
     */
    <T> T payload(Class<T> type);
}
