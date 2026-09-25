package dev.chaosaholic.core;

/**
 * Stacking rule: the same event rolled again while it is already active on the same target extends the running
 * instance instead of starting a duplicate. Different events stack freely (up to the per-player cap).
 */
public final class Stacking {
	/** remaining / total ticks of an instance after an extension (total only grows, so progress stays &lt;= 1). */
	public record Timer(int remaining, int total) {}

	private Stacking() {}

	/** remaining + added, capped at {@code cap}; never lower than the current remaining time. */
	public static Timer extend(int remaining, int total, int added, int cap) {
		long sum = (long) remaining + Math.max(0, added);
		int next = (int) Math.min(Math.max(cap, remaining), sum);
		return new Timer(next, Math.max(total, next));
	}

	/** First duration of a new instance: clamped into [1, cap]. */
	public static int initial(int duration, int cap) {
		return Math.max(1, Math.min(cap, duration));
	}

	/** Boss bar progress in [0, 1]. */
	public static float progress(int remaining, int total) {
		if (total <= 0) return 0.0F;
		return Math.max(0.0F, Math.min(1.0F, (float) remaining / total));
	}

	/** Whether a player with {@code active} timed events may receive another one. */
	public static boolean hasRoom(int active, int max) {
		return active < max;
	}
}
