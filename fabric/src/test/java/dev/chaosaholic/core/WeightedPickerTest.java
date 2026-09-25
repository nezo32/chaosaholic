package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WeightedPickerTest {
	record Ev(String id, int weight) {}

	private static final Ev A = new Ev("a", 100);
	private static final Ev B = new Ev("b", 300);
	private static final Ev ZERO = new Ev("zero", 0);
	private static final Ev NEG = new Ev("neg", -5);

	/** Returns the scripted values in order and fails on any extra or out-of-range call. */
	private static RandomIndex scripted(int... values) {
		Deque<Integer> queue = new ArrayDeque<>();
		for (int v : values) queue.add(v);
		return bound -> {
			if (bound <= 0) fail("nextInt(" + bound + ")");
			Integer v = queue.poll();
			if (v == null) fail("unexpected nextInt(" + bound + ")");
			if (v >= bound) fail("scripted " + v + " >= bound " + bound);
			return v;
		};
	}

	@Test
	void bucketsFollowWeights() {
		List<Ev> list = List.of(A, B);
		assertEquals(Optional.of(A), WeightedPicker.pick(list, Ev::weight, e -> true, scripted(0)));
		assertEquals(Optional.of(A), WeightedPicker.pick(list, Ev::weight, e -> true, scripted(99)));
		assertEquals(Optional.of(B), WeightedPicker.pick(list, Ev::weight, e -> true, scripted(100)));
		assertEquals(Optional.of(B), WeightedPicker.pick(list, Ev::weight, e -> true, scripted(399)));
	}

	@Test
	void zeroAndNegativeWeightsNeverPicked() {
		List<Ev> list = List.of(ZERO, A, NEG);
		Random random = new Random(1);
		for (int i = 0; i < 1000; i++) {
			assertEquals(Optional.of(A), WeightedPicker.pick(list, Ev::weight, e -> true, random::nextInt));
		}
		assertEquals(Optional.empty(), WeightedPicker.pick(List.of(ZERO, NEG), Ev::weight, e -> true, scripted()));
	}

	@Test
	void emptyListIsEmpty() {
		assertEquals(Optional.empty(), WeightedPicker.pick(List.<Ev>of(), Ev::weight, e -> true, scripted()));
	}

	@Test
	void failingCanStartRerollsAmongTheRest() {
		// first draw hits B (can't start); B is removed, total becomes 100: second draw picks A
		List<String> asked = new ArrayList<>();
		Optional<Ev> pick = WeightedPicker.pick(List.of(A, B), Ev::weight, e -> {
			asked.add(e.id());
			return e != B;
		}, scripted(250, 42));
		assertEquals(Optional.of(A), pick);
		assertEquals(List.of("b", "a"), asked);
	}

	@Test
	void noneCanStartIsEmptyAndEachAskedOnce() {
		List<String> asked = new ArrayList<>();
		Optional<Ev> pick = WeightedPicker.pick(List.of(A, B, new Ev("c", 1)), Ev::weight, e -> {
			asked.add(e.id());
			return false;
		}, new Random(7)::nextInt);
		assertEquals(Optional.empty(), pick);
		assertEquals(Set.of("a", "b", "c"), Set.copyOf(asked));
		assertEquals(3, asked.size());
	}

	@Test
	void disabledAsZeroWeightViaFunction() {
		Set<String> disabled = Set.of("b");
		Random random = new Random(3);
		for (int i = 0; i < 500; i++) {
			Optional<Ev> pick = WeightedPicker.pick(List.of(A, B), e -> disabled.contains(e.id()) ? 0 : e.weight(), e -> true, random::nextInt);
			assertEquals(Optional.of(A), pick);
		}
	}

	@Test
	void distributionRoughlyProportional() {
		Map<String, Integer> counts = new HashMap<>();
		Random random = new Random(42);
		int n = 40_000;
		for (int i = 0; i < n; i++) {
			Ev e = WeightedPicker.pick(List.of(A, B), Ev::weight, x -> true, random::nextInt).orElseThrow();
			counts.merge(e.id(), 1, Integer::sum);
		}
		double shareB = counts.get("b") / (double) n;
		assertTrue(Math.abs(shareB - 0.75) < 0.02, "share of b " + shareB);
	}

	@Test
	void maxWeightsDoNotOverflow() {
		List<Ev> many = new ArrayList<>();
		for (int i = 0; i < 40; i++) many.add(new Ev("e" + i, ChaosLimits.MAX_WEIGHT));
		Random random = new Random(9);
		for (int i = 0; i < 100; i++) assertTrue(WeightedPicker.pick(many, Ev::weight, e -> true, random::nextInt).isPresent());
	}
}
