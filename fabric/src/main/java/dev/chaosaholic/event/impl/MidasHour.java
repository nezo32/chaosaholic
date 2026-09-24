package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Good: Ores you mine drop double.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class MidasHour extends ChaosEvent {
	public MidasHour() {
		super("midas_hour", Category.GOOD, 60, 120);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(midas_hour): not implemented yet
	}
}
