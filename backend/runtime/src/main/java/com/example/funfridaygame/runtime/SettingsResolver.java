package com.example.funfridaygame.runtime;

import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameSettings;
import com.example.funfridaygame.sdk.SettingDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns submitted setting values (parsed JSON) into validated {@link GameSettings}: missing values
 * get defaults, unknown keys are ignored, every value is checked against its {@link SettingDef} and
 * finally the game's own cross-field rules run.
 */
public final class SettingsResolver {

    private SettingsResolver() {}

    /** Default value of every setting of a game. */
    public static Map<String, Object> defaults(GameDefinition definition) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (SettingDef def : definition.settings()) {
            values.put(def.key(), def.defaultValue());
        }
        return values;
    }

    /**
     * Validates and completes submitted settings.
     *
     * @param submitted raw values; may be {@code null} or partial
     * @throws InvalidSettingsException when a value is invalid
     */
    public static GameSettings resolve(GameDefinition definition, Map<String, ?> submitted) {
        Map<String, ?> input = submitted == null ? Map.of() : submitted;
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (SettingDef def : definition.settings()) {
            Object raw = input.get(def.key());
            if (raw == null) {
                values.put(def.key(), def.defaultValue());
                continue;
            }
            Object value = coerce(def, raw);
            if (value == null) {
                problems.add(def.label() + ": \"" + raw + "\" is not allowed");
                values.put(def.key(), def.defaultValue());
            } else {
                values.put(def.key(), value);
            }
        }
        if (problems.isEmpty()) {
            GameSettings settings = new GameSettings(values);
            problems.addAll(definition.validate(settings));
            if (problems.isEmpty()) {
                return settings;
            }
        }
        throw new InvalidSettingsException(problems);
    }

    private static Object coerce(SettingDef def, Object raw) {
        return switch (def.type()) {
            case SELECT -> matchOption(def, raw);
            case NUMBER -> {
                Integer value = toInt(raw);
                boolean inRange = value != null && value >= def.min() && value <= def.max();
                boolean onStep =
                        value != null
                                && (def.step() == null
                                        || def.step() <= 1
                                        || (value - def.min()) % def.step() == 0);
                yield inRange && onStep ? value : null;
            }
            case TOGGLE -> toBoolean(raw);
        };
    }

    private static Object matchOption(SettingDef def, Object raw) {
        for (SettingDef.Option option : def.options()) {
            Object expected = option.value();
            if (expected instanceof Integer number) {
                Integer value = toInt(raw);
                if (number.equals(value)) {
                    return number;
                }
            } else if (String.valueOf(expected).equals(String.valueOf(raw))) {
                return expected;
            }
        }
        return null;
    }

    private static Integer toInt(Object raw) {
        if (raw instanceof Integer value) {
            return value;
        }
        if (raw instanceof Number number) {
            double d = number.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE) {
                return (int) d;
            }
            return null;
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if ("true".equals(raw) || "false".equals(raw)) {
            return Boolean.valueOf((String) raw);
        }
        return null;
    }
}
