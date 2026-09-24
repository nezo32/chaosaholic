package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Night falls at once and lasts until the event ends.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class EternalNight extends ChaosEvent {
	public EternalNight() {
		super("eternal_night", Category.BAD, 60, 120);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(eternal_night): not implemented yet
	}
}
