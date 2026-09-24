package dev.chaosaholic.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShakeCurveTest {
	private static final int TOTAL = 15 * 20;

	@Test
	void neverAboveTheCap() {
		for (float elapsed = 0.0F; elapsed <= TOTAL; elapsed += 0.25F) {
			ShakeCurve.Angles a = ShakeCurve.angles(elapsed, TOTAL - elapsed);
			assertTrue(Math.abs(a.pitch()) <= ShakeCurve.MAX_DEGREES, "pitch at " + elapsed);
			assertTrue(Math.abs(a.yaw()) <= ShakeCurve.MAX_DEGREES, "yaw at " + elapsed);
			assertTrue(Math.abs(a.roll()) <= ShakeCurve.MAX_DEGREES, "roll at " + elapsed);
		}
		assertTrue(ShakeCurve.MAX_DEGREES <= 1.5F, "design limit 1.5 degrees");
	}

	@Test
	void reallyShakesInTheMiddle() {
		float max = 0.0F;
		for (float elapsed = 40.0F; elapsed < 80.0F; elapsed += 0.25F) {
			max = Math.max(max, Math.abs(ShakeCurve.angles(elapsed, TOTAL - elapsed).yaw()));
		}
		assertTrue(max > 1.0F, "noticeable wobble, got " + max);
	}

	@Test
	void silentAtTheEdgesAndForBadInput() {
		assertTrue(ShakeCurve.angles(0.0F, TOTAL).isNone(), "start");
		assertTrue(ShakeCurve.angles(TOTAL, 0.0F).isNone(), "end");
		assertTrue(ShakeCurve.angles(-5.0F, TOTAL).isNone(), "negative elapsed");
		assertTrue(ShakeCurve.angles(50.0F, -1.0F).isNone(), "over");
		assertTrue(ShakeCurve.angles(Float.NaN, 10.0F).isNone(), "NaN");
		assertEquals(0.0F, ShakeCurve.envelope(10.0F, Float.NaN));
	}

	@Test
	void easesInAndOutSmoothly() {
		float previous = 0.0F;
		for (float e = 0.0F; e <= ShakeCurve.EASE_IN_TICKS; e += 0.5F) {
			float s = ShakeCurve.envelope(e, TOTAL);
			assertTrue(s >= previous, "monotonic ease-in at " + e);
			assertTrue(s - previous < 0.2F, "no jump at " + e);
			previous = s;
		}
		assertEquals(1.0F, ShakeCurve.envelope(ShakeCurve.EASE_IN_TICKS, TOTAL));
		assertEquals(1.0F, ShakeCurve.envelope(100.0F, ShakeCurve.EASE_OUT_TICKS));
		assertTrue(ShakeCurve.envelope(100.0F, ShakeCurve.EASE_OUT_TICKS / 2.0F) < 1.0F, "fading out");
		assertTrue(ShakeCurve.envelope(100.0F, 1.0F) < 0.05F, "almost still just before the end");
	}

	/** Continuous in time: consecutive frames at 120 fps (1/6 tick) never jump by more than 0.6 degrees. */
	@Test
	void continuousFrameToFrame() {
		ShakeCurve.Angles last = ShakeCurve.angles(0.0F, TOTAL);
		for (float elapsed = 1.0F / 6.0F; elapsed <= TOTAL; elapsed += 1.0F / 6.0F) {
			ShakeCurve.Angles a = ShakeCurve.angles(elapsed, TOTAL - elapsed);
			assertTrue(Math.abs(a.yaw() - last.yaw()) < 0.6F, "yaw step at " + elapsed);
			assertTrue(Math.abs(a.pitch() - last.pitch()) < 0.6F, "pitch step at " + elapsed);
			assertTrue(Math.abs(a.roll() - last.roll()) < 0.6F, "roll step at " + elapsed);
			last = a;
		}
	}
}
