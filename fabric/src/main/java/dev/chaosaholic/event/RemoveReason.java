package dev.chaosaholic.event;

/**
 * Why a player stopped being affected by an event instance. {@link ChaosEvent#onPlayerRemoved} must revert every
 * per-player effect it applied by hand; effects, attribute modifiers and names applied through the instance's
 * trackers are reverted by the framework right after that call.
 */
public enum RemoveReason {
	/** The instance ended (any {@link StopReason}). */
	EVENT_ENDED,
	/** The player logged out (ServerPlayerEvents.LEAVE). */
	LOGOUT,
	/** The player died (the instance never follows the respawned player). */
	DEATH,
	/** The player moved to another dimension. */
	DIMENSION_CHANGE,
	/** The player switched to Creative or Spectator. */
	INELIGIBLE,
	/** /chaosaholic stop released the player from a world-scope instance that goes on for the other players. */
	STOPPED
}
