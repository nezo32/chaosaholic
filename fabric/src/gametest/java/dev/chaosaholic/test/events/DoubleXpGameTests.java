package dev.chaosaholic.test.events;

import dev.chaosaholic.event.EventRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests of double_xp. TODO(double_xp): replace this placeholder with real tests when the event is implemented
 * (start / effect / end / cleanup; see ARCHITECTURE.md, "Writing the event's gametests").
 */
public class DoubleXpGameTests {
	@GameTest
	public void registered(GameTestHelper h) {
		h.assertTrue(EventRegistry.get("double_xp") != null, "double_xp registered");
		h.succeed();
	}
}
