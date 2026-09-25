package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
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
import net.minecraft.world.level.GameType;

/** speed_demon: Speed III + Haste II while active, re-applied after milk, removed at the end / on Creative. */
public class SpeedDemonGameTests {
	@GameTest
	public void startGivesEffects(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "speed_demon", p);
		MobEffectInstance speed = p.getEffect(MobEffects.SPEED);
		MobEffectInstance haste = p.getEffect(MobEffects.HASTE);
		h.assertTrue(speed != null && speed.getAmplifier() == 2, "Speed III");
		h.assertTrue(haste != null && haste.getAmplifier() == 1, "Haste II");
		h.assertTrue(speed.getDuration() >= ev.remainingTicks(), "lasts the whole event");
		h.assertTrue(ev.totalTicks() >= 30 * 20 && ev.totalTicks() <= 60 * 20, "30-60 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.SPEED), "speed removed");
		h.assertFalse(p.hasEffect(MobEffects.HASTE), "haste removed");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void milkDoesNotEndIt(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		start(h, "speed_demon", p);
		p.removeAllEffects(); // drinking milk
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(p.hasEffect(MobEffects.SPEED), "re-applied"))
				.thenExecute(() -> cleanup(h, p))
				.thenSucceed();
	}

	@GameTest(maxTicks = 80)
	public void expiresAndCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "speed_demon", p);
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.SPEED), "speed removed");
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "bar removed");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** A longer effect from elsewhere (beacon, potion) is not taken away at the end. */
	@GameTest
	public void strongerForeignEffectIsKept(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "speed_demon", p);
		p.addEffect(new MobEffectInstance(MobEffects.HASTE, 20 * 600, 1)); // same level, 10 minutes
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(p.hasEffect(MobEffects.HASTE), "10-minute haste kept");
		h.assertFalse(p.hasEffect(MobEffects.SPEED), "our speed removed");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void creativeSwitchReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "speed_demon", p);
		p.setGameMode(GameType.CREATIVE);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.SPEED), "reverted");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
