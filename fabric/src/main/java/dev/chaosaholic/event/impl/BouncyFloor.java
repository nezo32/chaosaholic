package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: Landing bounces you like slime, with no fall damage.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class BouncyFloor extends ChaosEvent {
	public BouncyFloor() {
		super("bouncy_floor", Category.WEIRD, 30, 60);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(bouncy_floor): not implemented yet
	}
}
