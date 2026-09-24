package dev.chaosaholic.test.events;

import dev.chaosaholic.event.EventRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests of bee_swarm. TODO(bee_swarm): replace this placeholder with real tests when the event is implemented
 * (start / effect / end / cleanup; see ARCHITECTURE.md, "Writing the event's gametests").
 */
public class BeeSwarmGameTests {
	@GameTest
	public void registered(GameTestHelper h) {
		h.assertTrue(EventRegistry.get("bee_swarm") != null, "bee_swarm registered");
		h.succeed();
	}
}
