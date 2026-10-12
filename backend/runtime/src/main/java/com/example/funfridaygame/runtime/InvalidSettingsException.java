package com.example.funfridaygame.runtime;

import java.util.List;

/** The submitted settings don't fit the game's settings schema. */
public class InvalidSettingsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    public InvalidSettingsException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Human-readable problems. */
    public List<String> problems() {
        return problems;
    }
}
