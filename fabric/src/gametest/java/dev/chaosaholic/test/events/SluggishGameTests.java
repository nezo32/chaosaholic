package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** sluggish: Slowness II + Mining Fatigue I for 20-40 s; removed at the end / on logout; a longer foreign one kept. */
public class SluggishGameTests {
	@GameTest
	public void startGivesBothEffects(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "sluggish", p);
		MobEffectInstance slow = p.getEffect(MobEffects.SLOWNESS);
		MobEffectInstance fatigue = p.getEffect(MobEffects.MINING_FATIGUE);
		h.assertTrue(slow != null && slow.getAmplifier() == 1, "Slowness II");
		h.assertTrue(fatigue != null && fatigue.getAmplifier() == 0, "Mining Fatigue I");
		h.assertTrue(ev.totalTicks() >= 20 * 20 && ev.totalTicks() <= 40 * 20, "20-40 s, got " + ev.totalTicks());
		h.assertFalse(ev.event().hasWarning(), "no hazard, no warning");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.SLOWNESS), "slowness removed");
		h.assertFalse(p.hasEffect(MobEffects.MINING_FATIGUE), "fatigue removed");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void expiresAndCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "sluggish", p);
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.SLOWNESS), "slowness removed");
					h.assertFalse(p.hasEffect(MobEffects.MINING_FATIGUE), "fatigue removed");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void logoutReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		start(h, "sluggish", p);
		leave(h, p);
		h.assertFalse(p.hasEffect(MobEffects.SLOWNESS), "slowness removed on logout");
		h.assertFalse(p.hasEffect(MobEffects.MINING_FATIGUE), "fatigue removed on logout");
		h.succeed();
	}

	@GameTest
	public void longerForeignEffectIsKept(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "sluggish", p);
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 600, 1));
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(p.hasEffect(MobEffects.SLOWNESS), "10-minute slowness kept");
		h.assertFalse(p.hasEffect(MobEffects.MINING_FATIGUE), "our fatigue removed");
		cleanup(h, p);
		h.succeed();
	}
}
