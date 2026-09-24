package dev.chaosaholic.core;

/**
 * Anti-farming math. Each player has a "mark": the highest experience level reached since their last death.
 * Only levels above the mark start events, so spending levels (enchanting table, anvil) and earning them back
 * never triggers anything twice. The mark is persisted with the player and reset by death (not copied to the
 * respawned player); the first observation of a player without a mark initializes it to the current level, so
 * installing the mod on an existing world (or respawning with keepInventory) does not flood events.
 */
public final class LevelMark {
	/** triggers = events to start; mark = the new mark to store. */
	public record Result(int triggers, int mark) {}

	private LevelMark() {}

	/** Mark for a player seen for the first time (no stored mark): the current level, never negative. */
	public static int initial(int level) {
		return Math.max(0, level);
	}

	/**
	 * A level change from {@code oldLevel} to {@code newLevel} with the stored {@code mark}:
	 * {@code triggers = max(0, newLevel - max(oldLevel, mark))}, {@code mark = max(mark, newLevel)}.
	 * Level losses give 0 triggers and keep the mark.
	 */
	public static Result observe(int oldLevel, int newLevel, int mark) {
		int from = Math.max(oldLevel, mark);
		long diff = (long) newLevel - from;
		int triggers = (int) Math.max(0, Math.min(Integer.MAX_VALUE, diff));
		return new Result(triggers, Math.max(mark, newLevel));
	}
}
