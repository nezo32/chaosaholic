package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventIdsTest {
	@Test
	void validIds() {
		assertTrue(EventIds.isValid("speed_demon"));
		assertTrue(EventIds.isValid("tnt_rain"));
		assertTrue(EventIds.isValid("a2"));
	}

	@Test
	void invalidIds() {
		assertFalse(EventIds.isValid(null));
		assertFalse(EventIds.isValid(""));
		assertFalse(EventIds.isValid("a"));
		assertFalse(EventIds.isValid("Speed"));
		assertFalse(EventIds.isValid("speed-demon"));
		assertFalse(EventIds.isValid("1abc"));
		assertFalse(EventIds.isValid("x".repeat(33)));
		assertFalse(EventIds.isValid("a b"));
	}

	@Test
	void keys() {
		assertEquals("chaosaholic.event.tnt_rain", EventIds.nameKey("tnt_rain"));
		assertEquals("chaosaholic.event.tnt_rain.desc", EventIds.descKey("tnt_rain"));
		assertEquals("chaosaholic.event.tnt_rain.announce", EventIds.announceKey("tnt_rain"));
		assertEquals("chaosaholic.event.tnt_rain.warning", EventIds.warningKey("tnt_rain"));
	}

	@Test
	void limitsSanity() {
		assertEquals(0, ChaosLimits.clampWeight(-1));
		assertEquals(1000, ChaosLimits.clampWeight(5000));
		assertEquals(250, ChaosLimits.clampWeight(250));
		assertEquals(ChaosLimits.MAX_RADIUS, ChaosLimits.clampRadius(100));
		assertEquals(0.0, ChaosLimits.clampRadius(-1));
		assertTrue(ChaosLimits.WARNING_TICKS >= 60, "Hardcore rule: warning >= 3 s");
		assertEquals(20, ChaosLimits.seconds(1));
	}
}
