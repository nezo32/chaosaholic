package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Slowness II and Mining Fatigue I.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class Sluggish extends ChaosEvent {
	public Sluggish() {
		super("sluggish", Category.BAD, 20, 40);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(sluggish): not implemented yet
	}
}
