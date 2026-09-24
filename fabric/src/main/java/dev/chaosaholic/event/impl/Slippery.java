package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: Every block feels like ice.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class Slippery extends ChaosEvent {
	public Slippery() {
		super("slippery", Category.WEIRD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(slippery): not implemented yet
	}
}
