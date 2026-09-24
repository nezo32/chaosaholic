package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Warning;
import dev.chaosaholic.event.impl.AnvilRain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * anvil_rain: marks appear during the warning, anvils only after it, never on the player, capped damage, never
 * placed as blocks, capped count, removed when the event is stopped mid-fall.
 * Multi-tick tests allow 3 attempts: {@code /chaosaholic off} in ChaosModeGameTests stops every running event in
 * the parallel batch.
 */
public class AnvilRainGameTests {
	@GameTest(maxTicks = 300, maxAttempts = 3)
	public void marksDuringWarningThenAnvilsThatNeverBecomeBlocks(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "anvil_rain", p);
		int delay = Warning.delay(ev.context());
		List<FallingBlockEntity> anvils = new ArrayList<>();
		List<BlockPos> targets = new ArrayList<>();
		h.startSequence()
				.thenWaitUntil(() -> h.assertFalse(AnvilRain.targets(ev).isEmpty(), "a spot is marked"))
				.thenExecute(() -> {
					h.assertTrue(ev.age() < delay, "marked during the warning: age " + ev.age());
					h.assertValueEqual(ev.entities().count(), 0, "nothing falls during the warning");
					for (AnvilRain.Target t : AnvilRain.targets(ev)) {
						double dist = Math.sqrt(sq(t.pos.getX() + 0.5 - p.getX()) + sq(t.pos.getZ() + 0.5 - p.getZ()));
						h.assertTrue(dist >= AnvilRain.MIN_DISTANCE, "never on the player: " + dist);
					}
				})
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() >= 1, "an anvil falls"))
				.thenExecute(() -> {
					h.assertTrue(ev.age() >= delay, "no anvil before the warning ended: age " + ev.age());
					for (AnvilRain.Target t : AnvilRain.targets(ev)) {
						if (t.anvil() == null) continue;
						anvils.add(t.anvil());
						targets.add(t.pos);
						TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
						t.anvil().saveWithoutId(out);
						CompoundTag tag = out.buildResult();
						h.assertValueEqual(tag.getIntOr("FallHurtMax", -1), AnvilRain.MAX_DAMAGE, "damage cap");
						h.assertTrue(tag.getBooleanOr("CancelDrop", false), "never placed");
						h.assertFalse(tag.getBooleanOr("DropItem", true), "never dropped as an item");
					}
					h.assertFalse(anvils.isEmpty(), "anvil found on its target");
				})
				.thenWaitUntil(() -> h.assertTrue(anvils.getFirst().isRemoved(), "anvil landed"))
				.thenExecute(() -> {
					for (BlockPos t : targets) {
						for (BlockPos pos : BlockPos.betweenClosed(t.offset(-1, -2, -1), t.offset(1, 2, 1))) {
							h.assertFalse(h.getLevel().getBlockState(pos).is(BlockTags.ANVIL), "no anvil block at " + pos);
						}
					}
					manager(h).stop(ev, StopReason.FORCED);
					for (FallingBlockEntity a : anvils) h.assertTrue(a.isRemoved(), "removed at the end");
					h.assertValueEqual(ev.entities().count(), 0, "nothing owned left");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40, maxAttempts = 3)
	public void cappedAndStopMidFallRemovesAnvils(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "anvil_rain", p);
		for (int i = 0; i < 40; i++) AnvilRain.plan(ev, p);
		h.assertTrue(AnvilRain.planned(ev) <= AnvilRain.MAX_ANVILS, "capped: " + AnvilRain.planned(ev));
		h.assertTrue(AnvilRain.targets(ev).size() <= AnvilRain.MAX_ANVILS, "targets capped");
		BlockPos sky = h.absolutePos(new BlockPos(4, 30, 4));
		FallingBlockEntity anvil = AnvilRain.drop(ev, sky);
		h.assertTrue(anvil != null, "dropped");
		h.startSequence()
				.thenIdle(2)
				.thenExecute(() -> {
					h.assertFalse(anvil.isRemoved(), "still falling");
					manager(h).stop(ev, StopReason.FORCED);
					h.assertTrue(anvil.isRemoved(), "removed mid-fall");
					h.assertValueEqual(ev.entities().count(), 0, "nothing owned left");
					h.assertTrue(AnvilRain.targets(ev).stream().allMatch(t -> t.anvil() == null), "no anvil released after the stop");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void creativeNeverAffected(GameTestHelper h) {
		defaults(h);
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("anvil_rain"), creative).isEmpty(), "Creative is never affected");
		cleanup(h, creative);
		h.succeed();
	}

	private static double sq(double d) {
		return d * d;
	}
}
