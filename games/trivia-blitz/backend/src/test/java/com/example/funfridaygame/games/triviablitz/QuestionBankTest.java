package com.example.funfridaygame.games.triviablitz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The shipped question bank and the validation of bank files and questions. */
class QuestionBankTest {

    private static final QuestionBank BANK = QuestionBank.standard();

    @Test
    void theShippedBankIsLargeAndCoversEveryCategoryAndDifficulty() {
        assertThat(BANK.all()).hasSizeGreaterThanOrEqualTo(120);
        Map<Category, Long> perCategory =
                BANK.all().stream()
                        .collect(Collectors.groupingBy(Question::category, Collectors.counting()));
        assertThat(perCategory).containsOnlyKeys(Category.values());
        for (Category category : Category.values()) {
            // A single category must support the longest game (20 rounds) ...
            assertThat(BANK.count(new QuestionFilter(category, null)))
                    .as(category.label())
                    .isGreaterThanOrEqualTo(20);
            for (Difficulty difficulty : Difficulty.values()) {
                // ... and a category + difficulty pair the shortest one (5 rounds).
                assertThat(BANK.count(new QuestionFilter(category, difficulty)))
                        .as(category.label() + " · " + difficulty.label())
                        .isGreaterThanOrEqualTo(5);
            }
        }
    }

    @Test
    void everyShippedQuestionIsWellFormed() {
        for (Question question : BANK.all()) {
            assertThat(question.options()).hasSize(4).doesNotHaveDuplicates();
            assertThat(question.answer()).isBetween(0, 3);
            assertThat(question.text()).endsWith("?");
            assertThat(question.text().length()).isLessThanOrEqualTo(Question.MAX_TEXT);
        }
        assertThat(BANK.all().stream().map(q -> Question.normalize(q.text())).distinct().count())
                .isEqualTo(BANK.all().size());
    }

    @Test
    void parsesQuestionsAndIgnoresCommentsAndBlankLines() {
        QuestionBank bank =
                QuestionBank.parse(
                        List.of(
                                "# a comment",
                                "",
                                "science | easy | What is H₂O? | Water | Salt | Air | Fire | Two H, one O.",
                                "  food | hard | Which spice is the priciest? | Saffron | Salt | Pepper | Dill  "),
                        "test");

        assertThat(bank.all()).hasSize(2);
        Question first = bank.all().getFirst();
        assertThat(first.category()).isEqualTo(Category.SCIENCE);
        assertThat(first.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(first.correctOption()).isEqualTo("Water");
        assertThat(first.fact()).isEqualTo("Two H, one O.");
        assertThat(bank.all().get(1).fact()).isNull();
        assertThat(bank.all().get(1).options())
                .containsExactly("Saffron", "Salt", "Pepper", "Dill");
        assertThat(bank.count(new QuestionFilter(Category.FOOD, null))).isEqualTo(1);
        assertThat(bank.matching(new QuestionFilter(null, Difficulty.EASY))).containsExactly(first);
    }

    @Test
    void reportsEveryBrokenLineWithItsNumber() {
        List<String> lines =
                List.of(
                        "science | easy | Fine? | A | B | C | D",
                        "science | easy | Too few answers? | A | B | C",
                        "cooking | easy | Unknown category? | A | B | C | D",
                        "science | trivial | Unknown difficulty? | A | B | C | D",
                        "science | easy | Twice the same answer? | A | B | a | D",
                        "science | easy | A blank answer? | A |  | C | D",
                        "science | easy |   fine?  | W | X | Y | Z");

        assertThatThrownBy(() -> QuestionBank.parse(lines, "bank.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bank.txt:2: expected 7 or 8 fields")
                .hasMessageContaining("bank.txt:3: unknown category \"cooking\"")
                .hasMessageContaining("bank.txt:4: unknown difficulty \"trivial\"")
                .hasMessageContaining("bank.txt:5: the answer \"a\" appears twice")
                .hasMessageContaining("bank.txt:6: an answer is blank")
                .hasMessageContaining("bank.txt:7: duplicate of the question on line 1")
                .message()
                .doesNotContain("bank.txt:1:");
    }

    @Test
    void anEmptyBankIsRejected() {
        assertThatThrownBy(() -> QuestionBank.parse(List.of("# nothing here"), "empty.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no questions");
    }

    @Test
    void questionsEnforceFourAnswersAValidIndexAndShortTexts() {
        List<String> four = List.of("A", "B", "C", "D");
        assertThatThrownBy(() -> question(List.of("A", "B", "C"), 0, "Q?"))
                .hasMessageContaining("exactly 4 answers");
        assertThatThrownBy(() -> question(four, 4, "Q?")).hasMessageContaining("out of range");
        assertThatThrownBy(() -> question(four, -1, "Q?")).hasMessageContaining("out of range");
        assertThatThrownBy(() -> question(four, 0, " ")).hasMessageContaining("question is blank");
        assertThatThrownBy(() -> question(four, 0, "Q".repeat(Question.MAX_TEXT + 1) + "?"))
                .hasMessageContaining("longer than");
        String longAnswer = "x".repeat(Question.MAX_OPTION + 1);
        assertThatThrownBy(() -> question(List.of(longAnswer, "B", "C", "D"), 0, "Q?"))
                .hasMessageContaining("longer than");
    }

    @Test
    void shufflingKeepsTheCorrectAnswer() {
        Question original = question(List.of("Right", "Wrong 1", "Wrong 2", "Wrong 3"), 0, "Q?");
        Random random = new Random(3);
        int[] positions = new int[4];
        for (int i = 0; i < 200; i++) {
            Question shuffled = original.shuffled(random);
            assertThat(shuffled.correctOption()).isEqualTo("Right");
            assertThat(shuffled.options()).containsExactlyInAnyOrderElementsOf(original.options());
            positions[shuffled.answer()]++;
        }
        assertThat(Arrays.stream(positions).min().orElseThrow())
                .as("every position is used")
                .isGreaterThan(20);
    }

    private static Question question(List<String> options, int answer, String text) {
        return new Question(Category.GENERAL, Difficulty.EASY, text, options, answer, null);
    }
}
