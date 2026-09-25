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
import dev.chaosaholic.event.impl.BouncyFloor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * bouncy_floor: BOUNCINESS 0.8 + fall damage multiplier 0 (transient), fall damage cancelled (ender pearls still hurt),
 * all reverted; a player who leaves in the air gets a Slow Falling tail.
 */
public class BouncyFloorGameTests {
	/** Asserts the {@link SafeLanding} Slow Falling tail. */
	private static void assertTail(GameTestHelper h, ServerPlayer p) {
		MobEffectInstance tail = p.getEffect(MobEffects.SLOW_FALLING);
		h.assertTrue(tail != null, "airborne player gets a slow falling tail");
		h.assertTrue(tail.getDuration() <= SafeLanding.TAIL_TICKS, "tail is short: " + tail.getDuration());
	}
	private static boolean allowed(ServerPlayer p, DamageSource source) {
		return ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, source, 10.0F);
	}

	private static double bounciness(ServerPlayer p) {
		return p.getAttributeValue(Attributes.BOUNCINESS);
	}

	private static double fallMultiplier(ServerPlayer p) {
		return p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
	}

	@GameTest
	public void bouncesWithoutFallDamageThenReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		DamageSource fall = h.getLevel().damageSources().fall();
		ActiveEvent ev = start(h, "bouncy_floor", p);
		h.assertValueEqual(bounciness(p), BouncyFloor.BOUNCINESS, "bounciness");
		h.assertValueEqual(fallMultiplier(p), 0.0, "no fall damage multiplier");
		h.assertFalse(allowed(p, fall), "fall damage cancelled");
		h.assertTrue(allowed(p, h.getLevel().damageSources().generic()), "other damage applies");
		h.assertTrue(allowed(p, h.getLevel().damageSources().enderPearl()), "ender pearl damage applies");
		h.assertValueEqual(bounciness(other), 0.0, "other player untouched");
		h.assertTrue(allowed(other, fall), "other player still takes fall damage");
		p.setOnGround(true);
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.SLOW_FALLING), "on the ground: no slow falling tail");
		h.assertValueEqual(bounciness(p), 0.0, "bounciness reverted");
		h.assertValueEqual(fallMultiplier(p), 1.0, "fall damage multiplier reverted");
		h.assertTrue(allowed(p, fall), "fall damage after the end");
		cleanup(h, p, other);
		h.succeed();
	}

	/** Landing detection runs every tick on the server-side fall distance; it must cope with any sequence. */
	@GameTest(maxTicks = 60)
	public void landingTicksAndExpiry(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bouncy_floor", p);
		p.setOnGround(false);
		p.fallDistance = 4.0;
		ev.setRemainingTicks(10);
		h.startSequence()
				.thenIdle(3)
				.thenExecute(() -> p.setOnGround(true))
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertValueEqual(bounciness(p), 0.0, "reverted at expiry");
					h.assertValueEqual(fallMultiplier(p), 1.0, "reverted at expiry");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void logoutReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bouncy_floor", p);
		p.setOnGround(false); // mid-bounce, going up: the fall distance was reset by the landing
		leave(h, p);
		assertTail(h, p);
		h.assertValueEqual(bounciness(p), 0.0, "reverted on logout");
		h.assertValueEqual(fallMultiplier(p), 1.0, "reverted on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}

	@GameTest
	public void airborneStopGetsSoftLanding(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bouncy_floor", p);
		p.setOnGround(false);
		p.fallDistance = 20.0;
		manager(h).stop(ev, StopReason.FORCED);
		assertTail(h, p);
		h.assertValueEqual(fallMultiplier(p), 1.0, "fall damage multiplier reverted");
		cleanup(h, p);
		h.succeed();
	}
}
