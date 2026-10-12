package com.example.funfridaygame.sdk;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One host-editable setting. The frontend renders a form from these definitions and the platform
 * validates every submitted value against them, so games only ever see valid settings.
 *
 * @param key property name in {@link GameSettings}
 * @param label form label
 * @param type the kind of control
 * @param defaultValue value used when the host doesn't change it
 * @param options allowed values for {@link Type#SELECT}
 * @param min minimum for {@link Type#NUMBER}
 * @param max maximum for {@link Type#NUMBER}
 * @param step step for {@link Type#NUMBER}
 * @param help optional hint shown under the control
 */
public record SettingDef(
        String key,
        String label,
        Type type,
        Object defaultValue,
        List<Option> options,
        @Nullable Integer min,
        @Nullable Integer max,
        @Nullable Integer step,
        @Nullable String help) {

    /** The kind of form control. */
    public enum Type {
        /** One of {@link SettingDef#options()}. Values are strings or integers. */
        SELECT,
        /** An integer between {@link SettingDef#min()} and {@link SettingDef#max()}. */
        NUMBER,
        /** A boolean switch. */
        TOGGLE
    }

    /**
     * A choice of a {@link Type#SELECT} setting.
     *
     * @param value a {@code String} or an {@code Integer}
     * @param label display text
     */
    public record Option(Object value, String label) {
        public Option {
            Objects.requireNonNull(value, "value");
            label = label == null ? String.valueOf(value) : label;
        }
    }

    public SettingDef {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        options = options == null ? List.of() : List.copyOf(options);
        switch (type) {
            case SELECT -> {
                if (options.stream().noneMatch(o -> o.value().equals(defaultValue))) {
                    throw new IllegalArgumentException(
                            key + ": default must be one of the options");
                }
            }
            case NUMBER -> {
                if (min == null || max == null || !(defaultValue instanceof Integer value)) {
                    throw new IllegalArgumentException(
                            key + ": number needs min, max and int value");
                }
                if (value < min || value > max) {
                    throw new IllegalArgumentException(key + ": default outside min/max");
                }
            }
            case TOGGLE -> {
                if (!(defaultValue instanceof Boolean)) {
                    throw new IllegalArgumentException(key + ": toggle default must be boolean");
                }
            }
            default -> throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    /** A drop-down with fixed choices. */
    public static SettingDef select(
            String key, String label, Object defaultValue, Option... options) {
        return new SettingDef(
                key,
                label,
                Type.SELECT,
                defaultValue,
                Arrays.asList(options),
                null,
                null,
                null,
                null);
    }

    /** A drop-down whose choices are the given integers, labelled with an optional unit. */
    public static SettingDef selectInt(
            String key, String label, int defaultValue, String unit, int... values) {
        Option[] options =
                Arrays.stream(values)
                        .mapToObj(v -> new Option(v, unit == null ? String.valueOf(v) : v + unit))
                        .toArray(Option[]::new);
        return select(key, label, defaultValue, options);
    }

    /** An integer input. */
    public static SettingDef number(
            String key, String label, int defaultValue, int min, int max, int step) {
        return new SettingDef(
                key, label, Type.NUMBER, defaultValue, List.of(), min, max, step, null);
    }

    /** An on/off switch. */
    public static SettingDef toggle(String key, String label, boolean defaultValue) {
        return new SettingDef(
                key, label, Type.TOGGLE, defaultValue, List.of(), null, null, null, null);
    }

    /** Shortcut for {@link Option}. */
    public static Option option(Object value, String label) {
        return new Option(value, label);
    }

    /** Returns a copy with a help text. */
    public SettingDef withHelp(String text) {
        return new SettingDef(key, label, type, defaultValue, options, min, max, step, text);
    }
}
