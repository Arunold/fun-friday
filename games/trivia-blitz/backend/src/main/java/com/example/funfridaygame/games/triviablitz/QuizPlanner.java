package com.example.funfridaygame.games.triviablitz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Picks the questions of one game up front, so no question repeats within a game.
 *
 * <ul>
 *   <li>Categories are spread evenly (in random order): a mixed game visits every category before
 *       repeating one.
 *   <li>With a mixed difficulty the game gets an even share of easy, medium and hard questions and
 *       asks them in that order — it starts gentle and ramps up.
 * </ul>
 */
final class QuizPlanner {

    private QuizPlanner() {}

    /**
     * Plans a game.
     *
     * @param pool the questions allowed by the settings (no duplicates)
     * @param count questions wanted; fewer are returned only if the pool is smaller
     * @param rampUp whether to balance the difficulties and order them easy → hard
     * @param random the game's random source
     * @return distinct questions in the order they will be asked
     */
    static List<Question> plan(
            List<Question> pool, int count, boolean rampUp, RandomGenerator random) {
        List<Question> interleaved = interleaveCategories(pool, random);
        int wanted = Math.min(count, interleaved.size());
        if (!rampUp) {
            return List.copyOf(interleaved.subList(0, wanted));
        }
        return List.copyOf(rampUp(interleaved, wanted));
    }

    /** All questions, shuffled, then ordered round-robin by category. */
    static List<Question> interleaveCategories(List<Question> pool, RandomGenerator random) {
        List<Question> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        // Categories take turns in the (random) order in which they first appear.
        Map<Category, Deque<Question>> byCategory = new LinkedHashMap<>();
        for (Question question : shuffled) {
            byCategory.computeIfAbsent(question.category(), c -> new ArrayDeque<>()).add(question);
        }
        List<Question> result = new ArrayList<>(shuffled.size());
        while (result.size() < shuffled.size()) {
            for (Deque<Question> queue : byCategory.values()) {
                Question next = queue.poll();
                if (next != null) {
                    result.add(next);
                }
            }
        }
        return result;
    }

    /**
     * Takes {@code count} questions: an even quota per difficulty, easy first. Within each
     * difficulty, the least-used category so far goes next, so categories stay balanced.
     */
    private static List<Question> rampUp(List<Question> ordered, int count) {
        Map<Difficulty, List<Question>> byDifficulty = new EnumMap<>(Difficulty.class);
        for (Difficulty difficulty : Difficulty.values()) {
            byDifficulty.put(difficulty, new ArrayList<>());
        }
        for (Question question : ordered) {
            byDifficulty.get(question.difficulty()).add(question);
        }
        Map<Difficulty, Integer> quota = fit(quotas(count), byDifficulty);
        Map<Category, Integer> used = new EnumMap<>(Category.class);
        List<Question> picked = new ArrayList<>(count);
        for (Difficulty difficulty : Difficulty.values()) {
            List<Question> candidates = byDifficulty.get(difficulty);
            for (int i = 0; i < quota.get(difficulty); i++) {
                Question next = leastUsedCategory(candidates, used);
                candidates.remove(next);
                used.merge(next.category(), 1, Integer::sum);
                picked.add(next);
            }
        }
        return picked;
    }

    /** An even split of {@code count} over the difficulties, extra slots to the easier ones. */
    static Map<Difficulty, Integer> quotas(int count) {
        Map<Difficulty, Integer> quota = new EnumMap<>(Difficulty.class);
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            quota.put(levels[i], (count + levels.length - 1 - i) / levels.length);
        }
        return quota;
    }

    /** Moves the slots of difficulties without enough questions to the others (easier first). */
    private static Map<Difficulty, Integer> fit(
            Map<Difficulty, Integer> quota, Map<Difficulty, List<Question>> available) {
        int spare = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            int size = available.get(difficulty).size();
            if (quota.get(difficulty) > size) {
                spare += quota.get(difficulty) - size;
                quota.put(difficulty, size);
            }
        }
        for (Difficulty difficulty : Difficulty.values()) {
            int extra = Math.min(spare, available.get(difficulty).size() - quota.get(difficulty));
            quota.merge(difficulty, extra, Integer::sum);
            spare -= extra;
        }
        return quota;
    }

    /** The first candidate whose category has been asked the least so far. */
    private static Question leastUsedCategory(
            List<Question> candidates, Map<Category, Integer> used) {
        Question best = candidates.getFirst();
        int bestUses = Integer.MAX_VALUE;
        for (Question candidate : candidates) {
            int uses = used.getOrDefault(candidate.category(), 0);
            if (uses < bestUses) {
                best = candidate;
                bestUses = uses;
            }
        }
        return best;
    }
}
