package dev.chaosaholic.test.events;

import dev.chaosaholic.event.EventRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests of slippery. TODO(slippery): replace this placeholder with real tests when the event is implemented
 * (start / effect / end / cleanup; see ARCHITECTURE.md, "Writing the event's gametests").
 */
public class SlipperyGameTests {
	@GameTest
	public void registered(GameTestHelper h) {
		h.assertTrue(EventRegistry.get("slippery") != null, "slippery registered");
		h.succeed();
	}
}
