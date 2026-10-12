package com.example.funfridaygame.games.memorymatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Curated card faces. Every emoji is a single code point with default emoji presentation (no flags,
 * no ZWJ sequences, no variation selectors, Unicode 11 or older), so cards render the same on every
 * desktop. Look-alikes (e.g. two kinds of leaves) are left out on purpose: a memory game should
 * test memory, not eyesight.
 */
enum CardTheme {
    /** A bit of everything: all other themes together. */
    MIXED("mixed", "Mixed", ""),
    /** Animal faces and critters. */
    ANIMALS(
            "animals",
            "Animals",
            "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🦉 🦄 🐝 🦋 🐢 🐙 🐠 🐘 🦒 🦓 🐬 🦀 🐌"
                    + " 🐞"),
    /** Fruit, vegetables, snacks and sweets. */
    FOOD(
            "food",
            "Food",
            "🍎 🍌 🍇 🍓 🍒 🍍 🥝 🍉 🍋 🥑 🥕 🌽 🥨 🍕 🍔 🌭 🌮 🍟 🍣 🍩 🍪 🎂 🍦 🧁 🍫 🍿 🥐 🧀"
                    + " 🥞"),
    /** Things from home, office, music and sports. */
    OBJECTS(
            "objects",
            "Objects",
            "⚽ 🏀 🎾 🎈 🎁 🎨 🎸 🎺 🎲 🧩 🔑 🔔 💡 📷 📚 🚀 🚗 🚲 ⏰ 💎 👑 🎩 👓 🎧 📱 ⌚ 🧸 🎯 🏆"
                    + " 🔭 🧲 ☕"),
    /** Plants, weather and landscapes. */
    NATURE(
            "nature",
            "Nature",
            "🌸 🌻 🌷 🌹 🌵 🌲 🌴 🌱 🍀 🍁 🌾 🍄 🌰 🐚 🌈 🌙 ⭐ 🌞 🌍 🔥 💧 🌊 ⛄ 🌋 ⚡ ⛅ 🗻");

    private final String key;
    private final String label;
    private final List<String> faces;

    CardTheme(String key, String label, String faces) {
        this.key = key;
        this.label = label;
        this.faces = faces.isBlank() ? List.of() : List.of(faces.split(" "));
    }

    /** Setting value, e.g. {@code "animals"}. */
    String key() {
        return key;
    }

    /** Option label shown to the host. */
    String label() {
        return label;
    }

    /** Every face of this theme (the union of all other themes for {@link #MIXED}). */
    List<String> faces() {
        if (this != MIXED) {
            return faces;
        }
        Set<String> all = new LinkedHashSet<>();
        for (CardTheme theme : values()) {
            all.addAll(theme.faces);
        }
        return List.copyOf(all);
    }

    /**
     * Picks {@code count} different faces at random.
     *
     * @throws IllegalArgumentException when the theme has fewer faces
     */
    List<String> pick(int count, RandomGenerator random) {
        List<String> pool = new ArrayList<>(faces());
        if (count > pool.size()) {
            throw new IllegalArgumentException(
                    "Theme " + key + " has only " + pool.size() + " faces, " + count + " needed");
        }
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, count));
    }

    /**
     * The theme for a setting value.
     *
     * @throws IllegalArgumentException for unknown values (the platform validates settings first)
     */
    static CardTheme fromKey(String key) {
        return Arrays.stream(values())
                .filter(theme -> theme.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown theme " + key));
    }
}
