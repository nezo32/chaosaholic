package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: Your camera wobbles. You can turn screen effects off in the settings.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class ScreenShake extends ChaosEvent {
	public ScreenShake() {
		super("screen_shake", Category.WEIRD, 10, 15);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(screen_shake): not implemented yet
	}
}
