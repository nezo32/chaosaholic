package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Lit TNT falls around you, never right on top of you.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class TntRain extends ChaosEvent {
	public TntRain() {
		super("tnt_rain", Category.BAD, 15, 20);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(tnt_rain): not implemented yet
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every hazard
	}
}
