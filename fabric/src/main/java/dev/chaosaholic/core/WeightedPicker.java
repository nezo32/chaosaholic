package dev.chaosaholic.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Weighted random choice with rerolls: candidates with weight &lt;= 0 are never picked; a picked candidate that
 * fails {@code canStart} is dropped and the roll is repeated among the rest, so the result is always a candidate
 * that can start (or empty when none can).
 */
public final class WeightedPicker {
	private WeightedPicker() {}

	/**
	 * One draw per attempt: {@code random.nextInt(totalWeight)} over the remaining candidates in list order.
	 * Never calls {@code random.nextInt} with a bound &lt;= 0; calls {@code canStart} at most once per candidate.
	 */
	public static <T> Optional<T> pick(List<T> candidates, ToIntFunction<T> weight, Predicate<T> canStart, RandomIndex random) {
		List<T> remaining = new ArrayList<>(candidates.size());
		List<Integer> weights = new ArrayList<>(candidates.size());
		long total = 0;
		for (T c : candidates) {
			int w = weight.applyAsInt(c);
			if (w <= 0) continue;
			remaining.add(c);
			weights.add(w);
			total += w;
		}
		while (!remaining.isEmpty()) {
			int r = random.nextInt((int) Math.min(Integer.MAX_VALUE, total));
			int index = indexOf(weights, r);
			T c = remaining.remove(index);
			total -= weights.remove(index);
			if (canStart.test(c)) return Optional.of(c);
		}
		return Optional.empty();
	}

	/** Index of the bucket that contains {@code r} (0 &lt;= r &lt; sum). */
	static int indexOf(List<Integer> weights, int r) {
		int acc = 0;
		for (int i = 0; i < weights.size(); i++) {
			acc += weights.get(i);
			if (r < acc) return i;
		}
		return weights.size() - 1; // unreachable for r < sum
	}
}
