package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: A few angry bees come after you.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class BeeSwarm extends ChaosEvent {
	public BeeSwarm() {
		super("bee_swarm", Category.BAD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(bee_swarm): not implemented yet
	}
}
