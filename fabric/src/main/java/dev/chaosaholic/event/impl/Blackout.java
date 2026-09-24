package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Darkness closes in for a few seconds.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class Blackout extends ChaosEvent {
	public Blackout() {
		super("blackout", Category.BAD, 5, 10);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(blackout): not implemented yet
	}
}
