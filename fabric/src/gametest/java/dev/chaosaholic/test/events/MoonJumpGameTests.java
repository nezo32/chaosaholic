package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * moon_jump: Jump Boost III and no fall damage for the affected player only; both end with the event. Mock players
 * are invulnerable, so damage is checked through the Fabric ALLOW_DAMAGE event (what LivingEntity#hurt asks).
 */
public class MoonJumpGameTests {
	private static boolean allowed(ServerPlayer p, DamageSource source) {
		return ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, source, 10.0F);
	}

	@GameTest
	public void jumpBoostAndNoFallDamage(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		DamageSource fall = h.getLevel().damageSources().fall();
		ActiveEvent ev = start(h, "moon_jump", p);
		MobEffectInstance jump = p.getEffect(MobEffects.JUMP_BOOST);
		h.assertTrue(jump != null && jump.getAmplifier() == 2, "Jump Boost III");
		h.assertTrue(ev.totalTicks() >= 30 * 20 && ev.totalTicks() <= 60 * 20, "30-60 s, got " + ev.totalTicks());
		h.assertFalse(allowed(p, fall), "fall damage cancelled");
		h.assertTrue(allowed(p, h.getLevel().damageSources().generic()), "other damage still applies");
		h.assertTrue(allowed(other, fall), "players outside the event still take fall damage");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.JUMP_BOOST), "jump boost removed");
		h.assertTrue(allowed(p, fall), "fall damage after the end");
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void expiresAndCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "moon_jump", p);
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.JUMP_BOOST), "removed at the end");
					h.assertTrue(allowed(p, h.getLevel().damageSources().fall()), "no longer protected");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void creativeSwitchReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "moon_jump", p);
		p.setGameMode(GameType.CREATIVE);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.JUMP_BOOST), "reverted");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
