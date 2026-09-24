package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.SafeLanding;
import dev.chaosaholic.event.impl.GravityFlip;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * gravity_flip: Levitation II / Slow Falling phases, Slow Falling kept all along, height cap, a forced down phase at
 * the end, and a Slow Falling tail for players who leave the event in the air (mock players are never on the ground
 * unless the test says so).
 */
public class GravityFlipGameTests {
	private static boolean levitating(ServerPlayer p) {
		MobEffectInstance e = p.getEffect(MobEffects.LEVITATION);
		return e != null && e.getAmplifier() == GravityFlip.LEVITATION_AMPLIFIER;
	}

	private static void assertTail(GameTestHelper h, ServerPlayer p) {
		h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "no levitation left");
		MobEffectInstance tail = p.getEffect(MobEffects.SLOW_FALLING);
		h.assertTrue(tail != null, "airborne player keeps a slow falling tail");
		h.assertTrue(tail.getDuration() <= SafeLanding.TAIL_TICKS, "tail is short: " + tail.getDuration());
	}

	@GameTest(maxTicks = 200)
	public void floatsUpThenDriftsDown(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		h.assertFalse(GravityFlip.tooHigh(h.getLevel(), p.blockPosition()), "test player stands near the floor");
		ActiveEvent ev = start(h, "gravity_flip", p);
		h.assertTrue(levitating(p), "first phase: up");
		h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "slow falling kept all along");
		h.assertTrue(p.getEffect(MobEffects.LEVITATION).getDuration() <= GravityFlip.UP_TICKS + GravityFlip.MARGIN_TICKS,
				"levitation only for one phase (crash-safe)");
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "flipped down"))
				.thenExecute(() -> {
					h.assertTrue(ev.age() >= GravityFlip.UP_TICKS - 1, "flip after the up phase, age " + ev.age());
					h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "drifting down");
				})
				.thenWaitUntil(() -> h.assertTrue(levitating(p), "flipped up again"))
				.thenExecute(() -> {
					manager(h).stop(ev, StopReason.FORCED);
					assertTail(h, p);
					p.removeEffect(MobEffects.SLOW_FALLING);
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void onTheGroundNothingIsLeft(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.setOnGround(true);
		ActiveEvent ev = start(h, "gravity_flip", p);
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "levitation removed");
		h.assertFalse(p.hasEffect(MobEffects.SLOW_FALLING), "slow falling removed");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void alwaysEndsGoingDown(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "gravity_flip", p);
		h.assertTrue(levitating(p), "up");
		ev.setRemainingTicks(GravityFlip.FINAL_DOWN_TICKS);
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "forced down for the last seconds"))
				.thenIdle(GravityFlip.UP_TICKS + 5)
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "no flip up near the end");
					h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "slow falling");
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					assertTail(h, p);
					p.removeEffect(MobEffects.SLOW_FALLING);
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void heightCapStopsTheRise(GameTestHelper h) {
		defaults(h);
		// the empty test structure is air from relative y 0 to 7 (barrier floor at -1, ceiling at 8)
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 1 + GravityFlip.MAX_RISE, 1.5));
		h.assertTrue(GravityFlip.tooHigh(h.getLevel(), p.blockPosition()), "test player is above the cap");
		ActiveEvent ev = start(h, "gravity_flip", p);
		h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "no levitation above the cap");
		h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "drifts down instead");
		manager(h).stop(ev, StopReason.FORCED);
		p.removeEffect(MobEffects.SLOW_FALLING);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void logoutLeavesOnlyTheTail(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "gravity_flip", p);
		leave(h, p);
		assertTail(h, p); // saved with the player: they land softly after relogging
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}

	@GameTest
	public void creativeIsNeverAffected(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("gravity_flip"), p).isEmpty(), "refused for Creative");
		h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "no levitation");
		cleanup(h, p);
		h.succeed();
	}
}
