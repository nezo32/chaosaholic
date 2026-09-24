package dev.chaosaholic.test.events;

import dev.chaosaholic.event.EventRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests of screen_shake. TODO(screen_shake): replace this placeholder with real tests when the event is implemented
 * (start / effect / end / cleanup; see ARCHITECTURE.md, "Writing the event's gametests").
 */
public class ScreenShakeGameTests {
	@GameTest
	public void registered(GameTestHelper h) {
		h.assertTrue(EventRegistry.get("screen_shake") != null, "screen_shake registered");
		h.succeed();
	}
}
