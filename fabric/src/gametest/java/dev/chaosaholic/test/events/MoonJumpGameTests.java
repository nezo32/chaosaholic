package dev.chaosaholic.test.events;

import dev.chaosaholic.event.EventRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests of moon_jump. TODO(moon_jump): replace this placeholder with real tests when the event is implemented
 * (start / effect / end / cleanup; see ARCHITECTURE.md, "Writing the event's gametests").
 */
public class MoonJumpGameTests {
	@GameTest
	public void registered(GameTestHelper h) {
		h.assertTrue(EventRegistry.get("moon_jump") != null, "moon_jump registered");
		h.succeed();
	}
}
