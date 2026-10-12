package com.example.funfridaygame.games.wordscramble;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the game's bundled text resources: one entry per line, blank lines and # comments skipped.
 */
final class ResourceLines {

    private ResourceLines() {}

    /**
     * The content lines of a classpath resource.
     *
     * @param path absolute classpath path, e.g. {@code /word-scramble/words/6.txt}
     * @throws IllegalStateException when the resource is missing
     */
    static List<String> read(String path) {
        try (InputStream in = ResourceLines.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
