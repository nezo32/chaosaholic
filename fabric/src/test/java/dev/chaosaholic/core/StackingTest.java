package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StackingTest {
	private static final int CAP = ChaosLimits.MAX_REMAINING_TICKS;

	@Test
	void extensionAddsTime() {
		assertEquals(new Stacking.Timer(900, 1200), Stacking.extend(300, 1200, 600, CAP));
		assertEquals(new Stacking.Timer(1500, 1500), Stacking.extend(900, 1200, 600, CAP));
	}

	@Test
	void extensionIsCapped() {
		assertEquals(new Stacking.Timer(CAP, CAP), Stacking.extend(3000, 3000, 2400, CAP));
		assertEquals(new Stacking.Timer(CAP, CAP), Stacking.extend(CAP, CAP, 1200, CAP));
		assertEquals(3600, CAP);
	}

	@Test
	void extensionNeverShortens() {
		assertEquals(new Stacking.Timer(500, 600), Stacking.extend(500, 600, -100, CAP));
		assertEquals(new Stacking.Timer(500, 600), Stacking.extend(500, 600, 0, CAP));
	}

	@Test
	void repeatedExtensionsStayCapped() {
		Stacking.Timer t = new Stacking.Timer(600, 600);
		for (int i = 0; i < 100; i++) t = Stacking.extend(t.remaining(), t.total(), 1200, CAP);
		assertEquals(new Stacking.Timer(CAP, CAP), t);
	}

	@Test
	void initialIsClamped() {
		assertEquals(1, Stacking.initial(0, CAP));
		assertEquals(600, Stacking.initial(600, CAP));
		assertEquals(CAP, Stacking.initial(CAP * 2, CAP));
	}

	@Test
	void progress() {
		assertEquals(1.0F, Stacking.progress(600, 600));
		assertEquals(0.5F, Stacking.progress(300, 600));
		assertEquals(0.0F, Stacking.progress(0, 600));
		assertEquals(0.0F, Stacking.progress(5, 0));
		assertEquals(1.0F, Stacking.progress(700, 600));
	}

	@Test
	void roomUnderTheActiveCap() {
		assertTrue(Stacking.hasRoom(0, ChaosLimits.MAX_ACTIVE_PER_PLAYER));
		assertTrue(Stacking.hasRoom(7, 8));
		assertFalse(Stacking.hasRoom(8, 8));
		assertFalse(Stacking.hasRoom(9, 8));
	}
}
