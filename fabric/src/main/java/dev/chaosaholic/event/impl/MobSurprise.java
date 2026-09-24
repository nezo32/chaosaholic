package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: A wave of hostile mobs shows up nearby.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class MobSurprise extends ChaosEvent {
	public MobSurprise() {
		super("mob_surprise", Category.BAD, 60, 90);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(mob_surprise): not implemented yet
	}
}
