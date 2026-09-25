package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.Blackout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/** blackout: Darkness (not Blindness) for 5-10 s, never longer than 10 s after extensions; removed at the end. */
public class BlackoutGameTests {
	@GameTest
	public void darknessNotBlindness(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "blackout", p);
		h.assertTrue(p.hasEffect(MobEffects.DARKNESS), "darkness");
		h.assertFalse(p.hasEffect(MobEffects.BLINDNESS), "no blindness");
		h.assertTrue(ev.totalTicks() >= 5 * 20 && ev.totalTicks() <= 10 * 20, "5-10 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.DARKNESS), "removed on stop");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void extensionsStayShort(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "blackout", p);
		for (int i = 0; i < 4; i++) {
			ActiveEvent again = start(h, "blackout", p);
			h.assertTrue(again == ev, "extended, not duplicated");
		}
		h.assertTrue(ev.remainingTicks() <= Blackout.MAX_TICKS, "capped at 10 s, got " + ev.remainingTicks());
		h.assertTrue(ev.totalTicks() <= Blackout.MAX_TICKS, "total (boss bar) capped too, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.DARKNESS), "removed after an extended run");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void expiresAndCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "blackout", p);
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.DARKNESS), "removed at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void spectatorSwitchReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "blackout", p);
		p.setGameMode(GameType.SPECTATOR);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.DARKNESS), "reverted");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
