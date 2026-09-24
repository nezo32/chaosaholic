package dev.chaosaholic.core;

/**
 * Remaining time for boss bars: seconds (rounded up) under a minute ({@code chaosaholic.time.seconds}), otherwise
 * minutes and two-digit seconds ({@code chaosaholic.time.minutes}, "m:ss").
 */
public final class TimeFormat {
	/** minutes &lt; 0 = show {@code seconds} only; otherwise "minutes:seconds" with seconds zero-padded. */
	public record Parts(int minutes, int seconds) {
		public boolean underMinute() {
			return minutes < 0;
		}

		public String paddedSeconds() {
			return seconds < 10 ? "0" + seconds : Integer.toString(seconds);
		}
	}

	private TimeFormat() {}

	public static int ceilSeconds(int ticks) {
		if (ticks <= 0) return 0;
		return (ticks + ChaosLimits.TICKS_PER_SECOND - 1) / ChaosLimits.TICKS_PER_SECOND;
	}

	public static Parts parts(int ticks) {
		int total = ceilSeconds(ticks);
		if (total < 60) return new Parts(-1, total);
		return new Parts(total / 60, total % 60);
	}
}
