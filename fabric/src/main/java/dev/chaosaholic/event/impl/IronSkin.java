package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Good: Resistance II.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class IronSkin extends ChaosEvent {
	public IronSkin() {
		super("iron_skin", Category.GOOD, 30, 60);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(iron_skin): not implemented yet
	}
}
