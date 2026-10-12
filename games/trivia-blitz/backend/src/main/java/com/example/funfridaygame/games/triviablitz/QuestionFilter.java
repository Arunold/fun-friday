package com.example.funfridaygame.games.triviablitz;

/**
 * Which questions a game draws from, as chosen in the settings.
 *
 * @param category the only category to ask, or {@code null} for a mix of all categories
 * @param difficulty the only difficulty to ask, or {@code null} for a mix that ramps up from easy
 *     to hard
 */
record QuestionFilter(Category category, Difficulty difficulty) {

    /** Setting value for "all categories" / "all difficulties". */
    static final String MIXED = "mixed";

    /** Everything. */
    static final QuestionFilter ALL = new QuestionFilter(null, null);

    /**
     * Builds a filter from setting values.
     *
     * @param categoryId a {@link Category#id()} or {@value #MIXED}
     * @param difficultyId a {@link Difficulty#id()} or {@value #MIXED}
     * @throws IllegalArgumentException for unknown values
     */
    static QuestionFilter of(String categoryId, String difficultyId) {
        Category category =
                MIXED.equals(categoryId)
                        ? null
                        : Category.byId(categoryId)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Unknown category " + categoryId));
        Difficulty difficulty =
                MIXED.equals(difficultyId)
                        ? null
                        : Difficulty.byId(difficultyId)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Unknown difficulty " + difficultyId));
        return new QuestionFilter(category, difficulty);
    }

    /** Whether the question may be asked. */
    boolean matches(Question question) {
        return (category == null || question.category() == category)
                && (difficulty == null || question.difficulty() == difficulty);
    }

    /** Whether the difficulty is mixed (the game then ramps up from easy to hard). */
    boolean mixedDifficulty() {
        return difficulty == null;
    }

    /** Human-readable description for messages, e.g. "Science · Hard" or "Hard questions". */
    String describe() {
        if (category != null && difficulty != null) {
            return category.label() + " · " + difficulty.label();
        }
        if (category != null) {
            return category.label();
        }
        if (difficulty != null) {
            return difficulty.label() + " questions";
        }
        return "all questions";
    }
}
