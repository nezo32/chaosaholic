package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Good: A chest full of random loot lands nearby. It's yours to keep.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class ChestFromSky extends ChaosEvent {
	public ChestFromSky() {
		super("chest_from_sky", Category.GOOD, INSTANT, INSTANT);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(chest_from_sky): not implemented yet
	}
}
