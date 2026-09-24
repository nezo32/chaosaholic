package dev.chaosaholic.core;

/**
 * Pending event triggers of one player. Level-ups add triggers (capped); the manager takes one at a time, at least
 * {@code interval} ticks after the previous start. Triggers are counts, not rolled events: the event is rolled only
 * when it starts, so {@code canStart} sees the world as it is then. Not thread-safe (server thread only).
 */
public final class TriggerQueue {
	private final int cap;
	private final int interval;
	private int pending;
	private long nextStart = Long.MIN_VALUE;

	public TriggerQueue(int cap, int interval) {
		this.cap = cap;
		this.interval = interval;
	}

	/** Adds up to {@code count} triggers; returns how many were accepted (the rest is over the cap and dropped). */
	public int add(int count) {
		int accepted = Math.max(0, Math.min(count, cap - pending));
		pending += accepted;
		return accepted;
	}

	public int pending() {
		return pending;
	}

	/** True if a trigger is pending and the interval since the last start has passed. */
	public boolean ready(long now) {
		return pending > 0 && now >= nextStart;
	}

	/** Consumes one trigger started at tick {@code now}; the next may start at {@code now + interval}. */
	public void take(long now) {
		if (pending > 0) pending--;
		nextStart = now + interval;
	}

	/** Drops one trigger without starting anything (nothing could start); keeps the interval. */
	public void drop(long now) {
		take(now);
	}

	/** Postpones the next start (e.g. the player has no room for another active event). */
	public void delay(long now) {
		nextStart = now + interval;
	}

	public void clear() {
		pending = 0;
	}
}
