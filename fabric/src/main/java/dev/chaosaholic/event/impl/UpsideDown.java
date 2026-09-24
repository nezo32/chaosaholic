package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: Nearby mobs flip upside down.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class UpsideDown extends ChaosEvent {
	public UpsideDown() {
		super("upside_down", Category.WEIRD, 30, 60);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(upside_down): not implemented yet
	}
}
