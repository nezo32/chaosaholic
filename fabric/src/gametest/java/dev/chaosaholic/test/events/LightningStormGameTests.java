package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Warning;
import dev.chaosaholic.event.impl.LightningStorm;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * lightning_storm: nothing during the warning, then visual-only strikes ≥ 4 blocks from the player, no fire, capped.
 * Multi-tick tests allow 3 attempts: {@code /chaosaholic off} in ChaosModeGameTests stops every running event in
 * the parallel batch.
 */
public class LightningStormGameTests {
	@GameTest(maxTicks = 200, maxAttempts = 3)
	public void strikesAfterWarningAwayFromPlayerWithoutFire(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "lightning_storm", p);
		int delay = Warning.delay(ev.context());
		h.assertValueEqual(LightningStorm.planned(ev), 0, "nothing planned at start");
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(LightningStorm.strikes(ev).isEmpty(), "a bolt struck"))
				.thenExecute(() -> {
					h.assertTrue(ev.age() >= delay, "no strike before the warning ended: age " + ev.age());
					for (Vec3 s : LightningStorm.strikes(ev)) {
						double dist = Math.sqrt(sq(s.x - p.getX()) + sq(s.z - p.getZ()));
						h.assertTrue(dist >= LightningStorm.MIN_DISTANCE, "never on the player: " + dist);
					}
				})
				.thenIdle(5)
				.thenExecute(() -> {
					for (Vec3 s : LightningStorm.strikes(ev)) {
						for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(s).offset(-2, -1, -2), BlockPos.containing(s).offset(2, 2, 2))) {
							h.assertFalse(h.getLevel().getBlockState(pos).is(BlockTags.FIRE), "no fire at " + pos);
						}
					}
					manager(h).stop(ev, StopReason.FORCED);
					h.assertValueEqual(ev.entities().count(), 0, "no bolt left");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40, maxAttempts = 3)
	public void cappedAndStopRemovesBolts(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "lightning_storm", p);
		for (int i = 0; i < 40; i++) LightningStorm.plan(ev, p);
		h.assertTrue(LightningStorm.planned(ev) <= LightningStorm.MAX_STRIKES, "capped: " + LightningStorm.planned(ev));
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(LightningStorm.strikes(ev).isEmpty(), "planned bolts struck"))
				.thenExecute(() -> {
					h.assertTrue(LightningStorm.strikes(ev).size() <= LightningStorm.MAX_STRIKES, "strikes capped");
					h.assertTrue(ev.entities().list().stream().allMatch(e -> e instanceof LightningBolt), "only bolts owned");
					manager(h).stop(ev, StopReason.FORCED);
					h.assertValueEqual(ev.entities().count(), 0, "no bolt left");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void creativeNeverAffected(GameTestHelper h) {
		defaults(h);
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("lightning_storm"), creative).isEmpty(), "Creative is never affected");
		cleanup(h, creative);
		h.succeed();
	}

	private static double sq(double d) {
		return d * d;
	}
}
