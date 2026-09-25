package dev.chaosaholic.event;

/** Why an event instance ended. Every reason runs the same full cleanup. */
public enum StopReason {
	/** The timer ran out. */
	EXPIRED,
	/** An instant event finished its work (same tick it started). */
	INSTANT,
	/** /chaosaholic stop, a gametest, or code calling EventManager#stop. */
	FORCED,
	/** Every affected player left, died, changed dimension or became ineligible. */
	NO_PLAYERS,
	/** /chaosaholic off: the mode was switched off in this world. */
	MODE_OFF,
	/** SERVER_STOPPING: runs before the final save, so nothing temporary is written to disk. */
	SERVER_STOPPING,
	/** A hook of the event threw; the exception is logged. */
	ERROR
}
