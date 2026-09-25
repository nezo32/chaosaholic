package dev.chaosaholic.core;

/**
 * Every safety cap and timing constant of the event framework, in one place (ARCHITECTURE.md has the same table).
 * Pure: no Minecraft or Fabric. Times are in server ticks (20 per second).
 */
public final class ChaosLimits {
	public static final int TICKS_PER_SECOND = 20;

	/** Pending triggers per player; level-ups beyond this are dropped (a /xp add 1000 must not queue 1000 events). */
	public static final int QUEUE_CAP = 10;
	/** Gap between two queued events of one player: 3 levels at once = 3 events, 1 s apart. */
	public static final int START_INTERVAL_TICKS = 20;
	/** Concurrently active timed events affecting one player; further triggers wait in the queue. */
	public static final int MAX_ACTIVE_PER_PLAYER = 8;
	/** Upper bound of an event's remaining time, also after stacking extensions. */
	public static final int MAX_REMAINING_TICKS = 180 * TICKS_PER_SECOND;

	/** Per-world event weight range (/chaosaholic event &lt;id&gt; weight). 0 = never rolled. */
	public static final int MIN_WEIGHT = 0;
	public static final int MAX_WEIGHT = 1000;
	public static final int DEFAULT_WEIGHT = 100;

	/** Hard cap of any area query radius (Area.entities, Spots.near). */
	public static final double MAX_RADIUS = 24.0;
	/** Hard cap of the number of entities one area query returns. */
	public static final int MAX_AREA_ENTITIES = 64;
	/** Entities one event instance may own (spawned TNT, mobs, bees, sheep, chickens, ...). */
	public static final int MAX_OWNED_PER_EVENT = 32;
	/** Entities all event instances together may own. */
	public static final int MAX_OWNED_TOTAL = 256;
	/** Temporary blocks one event instance may place. */
	public static final int MAX_TEMP_BLOCKS_PER_EVENT = 256;
	/** Attempts of Spots.near before giving up. */
	public static final int SPOT_ATTEMPTS = 24;

	/** Bad events warn at least this long before the first hazard (Hardcore rule: reaction window &gt;= 3 s). */
	public static final int WARNING_TICKS = 3 * TICKS_PER_SECOND;

	/** Boss bar progress/name refresh interval. */
	public static final int BOSS_BAR_UPDATE_TICKS = 20;
	/** Tracked mob effects are re-checked (and re-applied if removed, e.g. by milk) this often. */
	public static final int EFFECT_REFRESH_TICKS = 20;
	/** Tracked effects are given with this many ticks on top of the event's remaining time. */
	public static final int EFFECT_MARGIN_TICKS = 10;
	/** Title timings (fade in, stay, fade out). */
	public static final int TITLE_FADE_IN = 5;
	public static final int TITLE_STAY = 40;
	public static final int TITLE_FADE_OUT = 10;

	private ChaosLimits() {}

	/** Clamps a weight into [MIN_WEIGHT, MAX_WEIGHT]. */
	public static int clampWeight(int weight) {
		return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
	}

	/** Clamps a radius into [0, MAX_RADIUS]. */
	public static double clampRadius(double radius) {
		return Math.max(0.0, Math.min(MAX_RADIUS, radius));
	}

	public static int seconds(int seconds) {
		return seconds * TICKS_PER_SECOND;
	}
}
