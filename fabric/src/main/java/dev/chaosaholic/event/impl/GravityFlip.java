package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: You float up and drift down by turns. It always ends softly.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class GravityFlip extends ChaosEvent {
	public GravityFlip() {
		super("gravity_flip", Category.WEIRD, 20, 30);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(gravity_flip): not implemented yet
	}
}
