package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.ScreenShake;
import dev.chaosaholic.net.ActiveEventsPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * screen_shake: the server side is a plain timed event whose entry in the synced active-events list drives the
 * client wobble (the wobble curve itself is covered by the JUnit ShakeCurveTest).
 */
public class ScreenShakeGameTests {
	private static boolean synced(GameTestHelper h, ServerPlayer p) {
		for (ActiveEventsPayload.Entry e : manager(h).entries(p)) {
			if (e.id().equals(ScreenShake.ID) && e.remaining() > 0 && e.remaining() <= e.total()) return true;
		}
		return false;
	}

	@GameTest(maxTicks = 40)
	public void syncedWhileActiveThenGone(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, ScreenShake.ID, p);
		h.assertTrue(ev.hasBossBar(), "timed, with a boss bar");
		h.assertTrue(ev.totalTicks() >= 10 * 20 && ev.totalTicks() <= 15 * 20, "10-15 s: " + ev.totalTicks());
		h.assertTrue(synced(h, p), "in the synced list the client reads");
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertFalse(synced(h, p), "gone from the synced list: the client stops shaking");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void forcedStopClearsTheEntry(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, ScreenShake.ID, p);
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(synced(h, p), "stopped: no entry");
		cleanup(h, p);
		h.succeed();
	}
}
