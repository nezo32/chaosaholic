package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerQueueTest {
	private static TriggerQueue queue() {
		return new TriggerQueue(ChaosLimits.QUEUE_CAP, ChaosLimits.START_INTERVAL_TICKS);
	}

	@Test
	void threeLevelsStartOneSecondApart() {
		TriggerQueue q = queue();
		assertEquals(3, q.add(3));
		long now = 100;
		int started = 0;
		for (long t = now; t < now + 100; t++) {
			if (q.ready(t)) {
				assertEquals(now + started * 20L, t, "start " + started);
				q.take(t);
				started++;
			}
		}
		assertEquals(3, started);
		assertEquals(0, q.pending());
	}

	@Test
	void capDropsOverflow() {
		TriggerQueue q = queue();
		assertEquals(10, q.add(1000));
		assertEquals(10, q.pending());
		assertEquals(0, q.add(1));
		q.take(0);
		assertEquals(1, q.add(5));
		assertEquals(10, q.pending());
		assertEquals(0, q.add(-3));
	}

	@Test
	void emptyIsNeverReady() {
		TriggerQueue q = queue();
		assertFalse(q.ready(0));
		assertFalse(q.ready(Long.MAX_VALUE));
	}

	@Test
	void delayPostponesWithoutConsuming() {
		TriggerQueue q = queue();
		q.add(1);
		assertTrue(q.ready(0));
		q.delay(0);
		assertFalse(q.ready(19));
		assertTrue(q.ready(20));
		assertEquals(1, q.pending());
	}

	@Test
	void dropConsumesAndKeepsInterval() {
		TriggerQueue q = queue();
		q.add(2);
		q.drop(0);
		assertEquals(1, q.pending());
		assertFalse(q.ready(10));
		assertTrue(q.ready(20));
	}

	@Test
	void clearEmpties() {
		TriggerQueue q = queue();
		q.add(4);
		q.clear();
		assertEquals(0, q.pending());
		assertFalse(q.ready(1000));
	}
}
