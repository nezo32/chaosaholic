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

/** iron_skin: Resistance II for 30-60 s, re-applied after milk, removed at the end / on logout. */
public class IronSkinGameTests {
	@GameTest
	public void startGivesResistanceTwo(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "iron_skin", p);
		MobEffectInstance res = p.getEffect(MobEffects.RESISTANCE);
		h.assertTrue(res != null && res.getAmplifier() == 1, "Resistance II");
		h.assertTrue(res.getDuration() >= ev.remainingTicks(), "lasts the whole event");
		h.assertTrue(ev.totalTicks() >= 30 * 20 && ev.totalTicks() <= 60 * 20, "30-60 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.RESISTANCE), "removed on stop");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void milkThenExpiry(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "iron_skin", p);
		p.removeAllEffects(); // drinking milk
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(p.hasEffect(MobEffects.RESISTANCE), "re-applied"))
				.thenExecute(() -> ev.setRemainingTicks(3))
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.RESISTANCE), "removed at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void logoutRevertsAndEnds(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "iron_skin", p);
		leave(h, p);
		h.assertFalse(p.hasEffect(MobEffects.RESISTANCE), "removed on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}
}
