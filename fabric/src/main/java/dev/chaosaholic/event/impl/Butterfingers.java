package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Every few seconds you may drop the item in your hand.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class Butterfingers extends ChaosEvent {
	public Butterfingers() {
		super("butterfingers", Category.BAD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(butterfingers): not implemented yet
	}
}
