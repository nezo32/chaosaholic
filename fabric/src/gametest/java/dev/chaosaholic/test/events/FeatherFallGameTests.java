package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.SafeLanding;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * feather_fall: the framework's ALLOW_DAMAGE listener is routed to the instance, which cancels fall damage of its
 * players only (fall damage type only: ender pearls still hurt), with a Slow Falling tail for a player who leaves in
 * the air. Mock players are invulnerable, so the test asks the Fabric event directly (what LivingEntity#hurt does).
 */
public class FeatherFallGameTests {
	/** Asserts the {@link SafeLanding} Slow Falling tail. */
	private static void assertTail(GameTestHelper h, ServerPlayer p) {
		MobEffectInstance tail = p.getEffect(MobEffects.SLOW_FALLING);
		h.assertTrue(tail != null, "airborne player gets a slow falling tail");
		h.assertTrue(tail.getDuration() <= SafeLanding.TAIL_TICKS, "tail is short: " + tail.getDuration());
	}
	private static boolean allowed(ServerPlayer p, DamageSource source) {
		return ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, source, 10.0F);
	}

	@GameTest
	public void cancelsFallDamageOfAffectedPlayersOnly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		DamageSource fall = h.getLevel().damageSources().fall();
		h.assertTrue(allowed(p, fall), "fall damage before");
		ActiveEvent ev = start(h, "feather_fall", p);
		h.assertFalse(allowed(p, fall), "fall damage cancelled");
		h.assertTrue(allowed(p, h.getLevel().damageSources().generic()), "other damage still applies");
		h.assertTrue(allowed(p, h.getLevel().damageSources().enderPearl()), "ender pearl damage still applies");
		h.assertTrue(allowed(other, fall), "players outside the event still take fall damage");
		p.setOnGround(true);
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.SLOW_FALLING), "on the ground: no slow falling tail");
		h.assertTrue(allowed(p, fall), "fall damage after the end");
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest
	public void endsOnLogout(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "feather_fall", p);
		h.assertTrue(ev.hasBossBar(), "timed");
		cleanup(h, p);
		h.assertTrue(ev.isStopped(), "stopped");
		h.assertTrue(allowed(p, h.getLevel().damageSources().fall()), "no longer protected");
		h.succeed();
	}

	@GameTest
	public void airborneStopGetsSoftLanding(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "feather_fall", p);
		p.setOnGround(false);
		p.fallDistance = 20.0;
		manager(h).stop(ev, StopReason.FORCED);
		assertTail(h, p);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void airborneLogoutKeepsTheTail(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "feather_fall", p);
		p.setOnGround(false);
		p.fallDistance = 20.0;
		leave(h, p);
		assertTail(h, p); // given before the save: they land softly after relogging
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}
}
