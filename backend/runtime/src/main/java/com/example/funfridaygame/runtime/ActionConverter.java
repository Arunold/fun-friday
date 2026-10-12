package com.example.funfridaygame.runtime;

import com.example.funfridaygame.sdk.InvalidActionException;

/** Turns a raw action payload (e.g. parsed JSON) into the record a game asks for. */
@FunctionalInterface
public interface ActionConverter {

    /**
     * Converts and validates a payload.
     *
     * @throws InvalidActionException when the payload doesn't fit {@code type}
     */
    <T> T convert(Object payload, Class<T> type);
}
