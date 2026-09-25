package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeFormatTest {
	@Test
	void secondsRoundUp() {
		assertEquals(0, TimeFormat.ceilSeconds(0));
		assertEquals(0, TimeFormat.ceilSeconds(-5));
		assertEquals(1, TimeFormat.ceilSeconds(1));
		assertEquals(1, TimeFormat.ceilSeconds(20));
		assertEquals(2, TimeFormat.ceilSeconds(21));
	}

	@Test
	void underAMinute() {
		TimeFormat.Parts p = TimeFormat.parts(59 * 20);
		assertTrue(p.underMinute());
		assertEquals(59, p.seconds());
		assertTrue(TimeFormat.parts(0).underMinute());
	}

	@Test
	void minutesAndPaddedSeconds() {
		TimeFormat.Parts p = TimeFormat.parts(65 * 20);
		assertFalse(p.underMinute());
		assertEquals(1, p.minutes());
		assertEquals("05", p.paddedSeconds());
		assertEquals("00", TimeFormat.parts(60 * 20).paddedSeconds());
		assertEquals(3, TimeFormat.parts(ChaosLimits.MAX_REMAINING_TICKS).minutes());
		assertEquals("42", TimeFormat.parts(102 * 20).paddedSeconds());
	}
}
