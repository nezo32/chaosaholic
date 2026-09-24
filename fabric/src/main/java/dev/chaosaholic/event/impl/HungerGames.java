package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;

/**
 * Bad: Hunger III: your food bar drains fast.
 *
 * <p>NOT IMPLEMENTED YET: {@link #canStart} returns false, so the event is never rolled and /chaosaholic trigger
 * refuses it. Implement it following ARCHITECTURE.md ("Event authoring guide"), then remove that override.
 */
public final class HungerGames extends ChaosEvent {
	public HungerGames() {
		super("hunger_games", Category.BAD, 30, 45);
	}

	@Override
	public boolean canStart(EventContext ctx) {
		return false; // TODO(hunger_games): not implemented yet
	}
}
