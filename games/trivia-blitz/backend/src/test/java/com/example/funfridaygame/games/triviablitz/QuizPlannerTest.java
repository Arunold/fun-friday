package com.example.funfridaygame.games.triviablitz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** How a game's questions are picked and ordered. */
class QuizPlannerTest {

    private static final List<Question> ALL = QuestionBank.standard().all();

    @Test
    void picksDistinctQuestionsRampingUpWithEvenCategories() {
        List<Question> plan = QuizPlanner.plan(ALL, 20, true, new Random(1));

        assertThat(plan).hasSize(20).doesNotHaveDuplicates();
        assertThat(plan).extracting(Question::difficulty).isSorted();
        assertThat(count(plan, Difficulty.EASY)).isEqualTo(7);
        assertThat(count(plan, Difficulty.MEDIUM)).isEqualTo(7);
        assertThat(count(plan, Difficulty.HARD)).isEqualTo(6);
        Map<Category, Long> perCategory =
                plan.stream()
                        .collect(Collectors.groupingBy(Question::category, Collectors.counting()));
        assertThat(perCategory.values()).as("every category twice").containsOnly(2L);
    }

    @Test
    void aShortMixedGameVisitsDifferentCategories() {
        for (int seed = 0; seed < 20; seed++) {
            List<Question> plan = QuizPlanner.plan(ALL, 5, true, new Random(seed));
            assertThat(plan).extracting(Question::category).doesNotHaveDuplicates();
            assertThat(plan)
                    .extracting(Question::difficulty)
                    .containsExactly(
                            Difficulty.EASY,
                            Difficulty.EASY,
                            Difficulty.MEDIUM,
                            Difficulty.MEDIUM,
                            Difficulty.HARD);
        }
    }

    @Test
    void aSingleDifficultyKeepsCategoriesTakingTurns() {
        List<Question> hard =
                QuestionBank.standard().matching(new QuestionFilter(null, Difficulty.HARD));
        List<Question> plan = QuizPlanner.plan(hard, 20, false, new Random(5));

        assertThat(plan).hasSize(20).doesNotHaveDuplicates();
        assertThat(plan.subList(0, 10)).extracting(Question::category).doesNotHaveDuplicates();
        assertThat(plan.subList(10, 20)).extracting(Question::category).doesNotHaveDuplicates();
    }

    @Test
    void movesSlotsToOtherDifficultiesWhenOneRunsShort() {
        List<Question> pool = new ArrayList<>();
        pool.add(question("Easy one?", Difficulty.EASY));
        for (int i = 0; i < 6; i++) {
            pool.add(question("Medium " + i + "?", Difficulty.MEDIUM));
            pool.add(question("Hard " + i + "?", Difficulty.HARD));
        }

        List<Question> plan = QuizPlanner.plan(pool, 10, true, new Random(2));

        assertThat(plan).hasSize(10).doesNotHaveDuplicates();
        assertThat(plan.getFirst().difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(plan).extracting(Question::difficulty).isSorted();
        assertThat(count(plan, Difficulty.EASY)).isEqualTo(1);
    }

    @Test
    void neverReturnsMoreThanThePool() {
        List<Question> pool = ALL.subList(0, 3);
        assertThat(QuizPlanner.plan(pool, 10, true, new Random(4))).hasSize(3);
        assertThat(QuizPlanner.plan(pool, 10, false, new Random(4))).hasSize(3);
    }

    @Test
    void quotasSplitEvenlyWithExtraSlotsForEasierQuestions() {
        assertThat(QuizPlanner.quotas(5))
                .containsEntry(Difficulty.EASY, 2)
                .containsEntry(Difficulty.MEDIUM, 2)
                .containsEntry(Difficulty.HARD, 1);
        assertThat(QuizPlanner.quotas(10))
                .containsEntry(Difficulty.EASY, 4)
                .containsEntry(Difficulty.MEDIUM, 3)
                .containsEntry(Difficulty.HARD, 3);
        assertThat(QuizPlanner.quotas(15))
                .containsOnly(
                        Map.entry(Difficulty.EASY, 5),
                        Map.entry(Difficulty.MEDIUM, 5),
                        Map.entry(Difficulty.HARD, 5));
    }

    @Test
    void theSameSeedGivesTheSamePlan() {
        assertThat(QuizPlanner.plan(ALL, 10, true, new Random(7)))
                .isEqualTo(QuizPlanner.plan(ALL, 10, true, new Random(7)))
                .isNotEqualTo(QuizPlanner.plan(ALL, 10, true, new Random(8)));
    }

    private static long count(List<Question> plan, Difficulty difficulty) {
        return plan.stream().filter(q -> q.difficulty() == difficulty).count();
    }

    private static Question question(String text, Difficulty difficulty) {
        return new Question(
                Category.GENERAL, difficulty, text, List.of("A", "B", "C", "D"), 0, null);
    }
}
