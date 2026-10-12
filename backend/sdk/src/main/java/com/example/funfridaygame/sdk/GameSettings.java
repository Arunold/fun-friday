package com.example.funfridaygame.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Validated settings of a game run. Every key declared by the game's {@link SettingDef}s is present
 * and has the right type.
 */
public final class GameSettings {

    /** Number of rounds; read by the platform. */
    public static final String ROUNDS = "rounds";

    /** Default turn length in seconds; read by the platform when present. */
    public static final String ROUND_DURATION = "roundDuration";

    private final Map<String, Object> values;

    public GameSettings(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Whether a value exists for {@code key}. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** An integer setting. */
    public int getInt(String key) {
        if (require(key) instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Setting " + key + " is not a number");
    }

    /** A boolean setting. */
    public boolean getBoolean(String key) {
        if (require(key) instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException("Setting " + key + " is not a boolean");
    }

    /** A string setting. */
    public String getString(String key) {
        return String.valueOf(require(key));
    }

    /** A string setting mapped to an enum constant of the same name. */
    public <E extends Enum<E>> E getEnum(String key, Class<E> type) {
        return Enum.valueOf(type, getString(key));
    }

    /** Rounds in the game (1 when the game doesn't declare the setting). */
    public int rounds() {
        return has(ROUNDS) ? getInt(ROUNDS) : 1;
    }

    /** Default turn length, when the game declares {@link #ROUND_DURATION}. */
    public OptionalInt roundDurationSeconds() {
        return has(ROUND_DURATION) ? OptionalInt.of(getInt(ROUND_DURATION)) : OptionalInt.empty();
    }

    /** All values, read-only. */
    public Map<String, Object> asMap() {
        return values;
    }

    private Object require(String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GameSettings settings && values.equals(settings.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
