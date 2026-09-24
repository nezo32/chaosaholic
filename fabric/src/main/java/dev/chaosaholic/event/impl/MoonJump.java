package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Good: Jump Boost III and no fall damage.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class MoonJump extends ChaosEvent {
	public MoonJump() {
		super("moon_jump", Category.GOOD, 30, 60);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(moon_jump): not implemented yet
	}
}
