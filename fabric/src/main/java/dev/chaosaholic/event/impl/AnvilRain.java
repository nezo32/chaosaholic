package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Anvils crash down on marked spots nearby.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class AnvilRain extends ChaosEvent {
	public AnvilRain() {
		super("anvil_rain", Category.BAD, 15, 20);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(anvil_rain): not implemented yet
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every hazard
	}
}
