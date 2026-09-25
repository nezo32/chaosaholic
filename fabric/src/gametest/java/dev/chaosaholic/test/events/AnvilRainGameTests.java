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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * anvil_rain: marks appear during the warning, anvils only after it, never on the player, capped damage, never
 * placed as blocks, capped count, removed when the event is stopped mid-fall.
 *
 * <p>The event picks targets up to {@link AnvilRain#MAX_DISTANCE} blocks from the player, mostly outside the 8x8 test
 * structure, where entities may not tick (only the structure's chunks are force-loaded; mock players never tick, so
 * they load nothing around them). So the tests check the anvils the event releases right away and after a forced
 * stop, and watch an anvil land only when it was dropped inside the structure.
 */
public class AnvilRainGameTests {
	@GameTest(maxTicks = 200)
	public void marksDuringWarningThenAnvilsThatNeverBecomeBlocks(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "anvil_rain", p);
		int delay = Warning.delay(ev.context());
		List<FallingBlockEntity> anvils = new ArrayList<>();
		// inside the structure (air from relative y 0 to 7 over a barrier floor), 4.9 blocks from the player
		BlockPos inside = h.absolutePos(new BlockPos(5, 0, 5));
		FallingBlockEntity[] landing = new FallingBlockEntity[1];
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
						assertNeverABlock(h, t.anvil());
					}
					h.assertFalse(anvils.isEmpty(), "anvil found on its target");
					landing[0] = AnvilRain.drop(ev, inside);
					h.assertTrue(landing[0] != null, "anvil released inside the structure");
					assertNeverABlock(h, landing[0]);
				})
				.thenWaitUntil(() -> h.assertTrue(landing[0].isRemoved(), "anvil landed"))
				.thenExecute(() -> {
					for (BlockPos pos : BlockPos.betweenClosed(inside.offset(-1, -1, -1), inside.offset(1, 7, 1))) {
						h.assertFalse(h.getLevel().getBlockState(pos).is(BlockTags.ANVIL), "no anvil block at " + pos);
					}
					manager(h).stop(ev, StopReason.FORCED);
					for (FallingBlockEntity a : anvils) h.assertTrue(a.isRemoved(), "removed at the end");
					h.assertValueEqual(ev.entities().count(), 0, "nothing owned left");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** The saved flags that keep a falling anvil of the event from ever becoming a block or an item, and its damage cap. */
	private static void assertNeverABlock(GameTestHelper h, FallingBlockEntity anvil) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
		anvil.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		h.assertValueEqual(tag.getIntOr("FallHurtMax", -1), AnvilRain.MAX_DAMAGE, "damage cap");
		h.assertTrue(tag.getBooleanOr("CancelDrop", false), "never placed");
		h.assertFalse(tag.getBooleanOr("DropItem", true), "never dropped as an item");
	}

	@GameTest(maxTicks = 40)
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

	/** Hardcore path of allowDamage: a lethal hit by an anvil of this instance is cancelled, other hits are not. */
	@GameTest
	public void hardcoreCancelsOnlyLethalOwnedAnvils(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "anvil_rain", p);
		FallingBlockEntity anvil = AnvilRain.drop(ev, h.absolutePos(new BlockPos(5, 30, 5)));
		h.assertTrue(anvil != null, "anvil released in the sky");
		DamageSource ours = h.getLevel().damageSources().anvil(anvil);
		h.assertTrue(AnvilRain.isLethalHit(ev, p, ours, p.getHealth()), "lethal hit of our anvil");
		h.assertFalse(AnvilRain.isLethalHit(ev, p, ours, p.getHealth() - 1.0F), "survivable hit goes through");
		FallingBlockEntity other = FallingBlockEntity.fall(h.getLevel(), h.absolutePos(new BlockPos(6, 30, 6)), Blocks.ANVIL.defaultBlockState());
		h.assertFalse(AnvilRain.isLethalHit(ev, p, h.getLevel().damageSources().anvil(other), p.getHealth()), "someone else's anvil");
		other.discard();
		h.assertTrue(event("anvil_rain").allowDamage(ev, p, ours, p.getHealth()) != ev.context().isHardcore(), "cancelled only on Hardcore");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(anvil.isRemoved(), "anvil removed");
		cleanup(h, p);
		h.succeed();
	}

	/** Targets are never next to a bystander (a player the event does not affect) either. */
	@GameTest
	public void neverOnABystander(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ServerPlayer bystander = survivalPlayer(h, new Vec3(7.5, 2, 1.5));
		ActiveEvent ev = start(h, "anvil_rain", p);
		h.assertFalse(ev.isAffected(bystander), "bystander not affected");
		h.assertTrue(AnvilRain.drop(ev, bystander.blockPosition()) == null, "no anvil onto the bystander");
		h.assertValueEqual(ev.entities().count(), 0, "nothing released");
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p, bystander);
		h.succeed();
	}
}
