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
import net.minecraft.world.level.GameType;

/** healing_aura: Regeneration II for 20-40 s, removed at the end / on logout / on Creative, foreign longer one kept. */
public class HealingAuraGameTests {
	@GameTest
	public void startGivesRegenerationTwo(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "healing_aura", p);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		h.assertTrue(regen != null && regen.getAmplifier() == 1, "Regeneration II");
		h.assertTrue(regen.getDuration() >= ev.remainingTicks(), "lasts the whole event");
		h.assertTrue(ev.totalTicks() >= 20 * 20 && ev.totalTicks() <= 40 * 20, "20-40 s, got " + ev.totalTicks());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(p.hasEffect(MobEffects.REGENERATION), "removed on stop");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 80)
	public void expiresAndCleansUp(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "healing_aura", p);
		ev.setRemainingTicks(45); // crosses a heart-particle tick and an effect refresh on the way
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.REGENERATION), "removed at the end");
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "bar removed");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void logoutReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		start(h, "healing_aura", p);
		leave(h, p);
		h.assertFalse(p.hasEffect(MobEffects.REGENERATION), "removed on logout");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void creativeSwitchReverts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "healing_aura", p);
		p.setGameMode(GameType.CREATIVE);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(MobEffects.REGENERATION), "reverted");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** A potion drunk during the event (same level, longer) is not taken away at the end. */
	@GameTest
	public void longerForeignEffectIsKept(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "healing_aura", p);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 600, 1));
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(p.hasEffect(MobEffects.REGENERATION), "10-minute regeneration kept");
		cleanup(h, p);
		h.succeed();
	}
}
