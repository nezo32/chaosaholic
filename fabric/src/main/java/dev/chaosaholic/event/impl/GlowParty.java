package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Weird: Everything nearby glows, even through walls.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class GlowParty extends ChaosEvent {
	public GlowParty() {
		super("glow_party", Category.WEIRD, 30, 60);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(glow_party): not implemented yet
	}
}
