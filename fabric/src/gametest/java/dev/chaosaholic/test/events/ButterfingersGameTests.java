package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.Butterfingers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * butterfingers: the drop itself (tossed, not destroyed, short pickup delay), its conditions and the per-run cap, and
 * that the item never lands in lava or over a drop (lava ahead, overhang edges, no floor, no safe place at all).
 * The random roll is bypassed through {@link Butterfingers#attempt} with {@code lucky = true}.
 */
public class ButterfingersGameTests {
	private static List<ItemEntity> dropped(GameTestHelper h, ServerPlayer p) {
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0),
				e -> e.getItem().is(Items.NAME_TAG));
	}

	private static void hold(ServerPlayer p, int count) {
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NAME_TAG, count));
	}

	/** Stone floor at relative y = 1 under the whole structure (the player stands on it at y = 2). */
	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		}
	}

	/** Every name-tag item entity inside the structure. */
	private static List<ItemEntity> itemsInStructure(GameTestHelper h) {
		AABB box = new AABB(h.absoluteVec(new Vec3(0, 0, 0)), h.absoluteVec(new Vec3(8, 8, 8)));
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.NAME_TAG));
	}

	/**
	 * A lava pool at relative y = 1 over x in [x0, x1], z in [z0, z1], with a stone ring around it and stone below, so
	 * it cannot flow anywhere (never out of the structure).
	 */
	private static void lavaPool(GameTestHelper h, int x0, int x1, int z0, int z1) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1;
				h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				h.setBlock(new BlockPos(x, 1, z), inside ? Blocks.LAVA : Blocks.STONE);
			}
		}
	}

	private static void removeLava(GameTestHelper h, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
	}

	/**
	 * A survival player standing at {@code relative}, on the ground, looking horizontally along the structure's
	 * relative +x (the structure may be rotated, so the yaw comes from the absolute direction).
	 */
	private static ServerPlayer facingPlusX(GameTestHelper h, Vec3 relative) {
		ServerPlayer p = survivalPlayer(h, relative);
		Vec3 dir = h.absoluteVec(relative.add(1.0, 0.0, 0.0)).subtract(h.absoluteVec(relative));
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		p.snapTo(p.getX(), p.getY(), p.getZ(), yaw, 0.0F);
		p.setOnGround(true);
		return p;
	}

	/**
	 * Lets the item fly for {@code ticks}, then checks it still exists with the whole stack and lies on stone (not in
	 * lava), removes the lava and the item, and ends the test.
	 */
	private static void assertSurvives(GameTestHelper h, ServerPlayer p, ActiveEvent ev, int count, Runnable restore) {
		h.runAfterDelay(50, () -> {
			List<ItemEntity> items = itemsInStructure(h);
			h.assertValueEqual(items.size(), 1, "the item still exists");
			ItemEntity item = items.getFirst();
			h.assertValueEqual(item.getItem().getCount(), count, "whole stack");
			h.assertFalse(item.isInLava() || item.isOnFire(), "not in lava");
			BlockPos under = BlockPos.containing(item.getX(), item.getY() - 0.1, item.getZ());
			h.assertTrue(h.getLevel().getBlockState(under).is(Blocks.STONE), "lies on stone: " + item.position());
			items.forEach(ItemEntity::discard);
			restore.run();
			manager(h).stop(ev, StopReason.FORCED);
			cleanup(h, p);
			h.succeed();
		});
	}

	@GameTest
	public void dropsTheWholeStackAsAnItem(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h);
		p.setOnGround(true);
		hold(p, 3);
		ActiveEvent ev = start(h, "butterfingers", p);
		h.assertTrue(p.getMainHandItem().getCount() == 3, "no drop right at the start");
		h.assertTrue(Butterfingers.attempt(ev, p, true), "dropped");
		h.assertTrue(p.getMainHandItem().isEmpty(), "hand empty");
		List<ItemEntity> items = dropped(h, p);
		h.assertValueEqual(items.size(), 1, "one item entity");
		ItemEntity item = items.getFirst();
		h.assertValueEqual(item.getItem().getCount(), 3, "whole stack, nothing destroyed");
		h.assertTrue(item.hasPickUpDelay(), "short pickup delay");
		h.assertTrue(item.getOwner() == p, "thrown by the player");
		item.discard();
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void conditionsAndCap(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		ActiveEvent ev = start(h, "butterfingers", p);
		hold(p, 1);
		h.assertFalse(Butterfingers.attempt(ev, p, false), "unlucky roll: kept");
		p.setOnGround(false);
		h.assertFalse(Butterfingers.attempt(ev, p, true), "airborne: kept");
		p.setOnGround(true);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		h.assertFalse(Butterfingers.attempt(ev, p, true), "empty hand: nothing");
		other.setOnGround(true);
		hold(other, 1);
		h.assertFalse(Butterfingers.attempt(ev, other, true), "not affected: kept");
		int drops = 0;
		for (int i = 0; i < Butterfingers.MAX_DROPS + 2; i++) {
			hold(p, 1);
			if (Butterfingers.attempt(ev, p, true)) drops++;
		}
		h.assertValueEqual(drops, Butterfingers.MAX_DROPS, "capped drops per run");
		h.assertValueEqual(Butterfingers.dropsLeft(ev, p), 0, "none left");
		h.assertFalse(p.getMainHandItem().isEmpty(), "kept after the cap");
		dropped(h, p).forEach(ItemEntity::discard);
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p, other);
		h.succeed();
	}

	/** Open, safe floor ahead: tossed forward (about 1.7 blocks), lands on the floor and survives. */
	@GameTest(maxTicks = 80)
	public void tossedForwardOnSafeGround(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = facingPlusX(h, new Vec3(1.5, 2, 1.5));
		hold(p, 4);
		ActiveEvent ev = start(h, "butterfingers", p);
		Butterfingers.Toss toss = Butterfingers.plan(h.getLevel(), p);
		h.assertTrue(toss != null && toss.velocity().horizontalDistance() > 0.1, "tossed forward: " + toss);
		h.assertTrue(Butterfingers.attempt(ev, p, true), "dropped");
		h.runAfterDelay(40, () -> {
			List<ItemEntity> items = itemsInStructure(h);
			h.assertValueEqual(items.size(), 1, "item exists");
			double d = items.getFirst().position().subtract(p.position()).horizontalDistance();
			h.assertTrue(d > 1.0 && d < Butterfingers.FLIGHT_REACH, "landed ahead within the checked reach: " + d);
		});
		assertSurvives(h, p, ev, 4, () -> {});
	}

	/**
	 * Review finding: lava two blocks ahead (the item used to land about 2.4 blocks ahead, in the lava). The whole
	 * flight is checked now: the stack is set down on the stone under the player instead.
	 */
	@GameTest(maxTicks = 80)
	public void lavaTwoBlocksAheadIsNeverHit(GameTestHelper h) {
		defaults(h);
		floor(h);
		lavaPool(h, 3, 5, 1, 3); // player at x 1.5: the lava starts 1.5 blocks ahead, the old landing point is in it
		ServerPlayer p = facingPlusX(h, new Vec3(1.5, 2, 1.5));
		hold(p, 5);
		ActiveEvent ev = start(h, "butterfingers", p);
		Butterfingers.Toss toss = Butterfingers.plan(h.getLevel(), p);
		h.assertTrue(toss != null && toss.velocity().equals(Vec3.ZERO), "set down, not tossed: " + toss);
		h.assertTrue(Butterfingers.attempt(ev, p, true), "dropped");
		assertSurvives(h, p, ev, 5, () -> removeLava(h, 3, 5, 1, 3));
	}

	/**
	 * Review finding: a player leaning over the edge of an overhang above lava (centre over the lava, box still on the
	 * stone). The item is set down on the stone block that carries the player, not at the centre over the edge.
	 */
	@GameTest(maxTicks = 80)
	public void overhangEdgeKeepsTheItemOnTheBlock(GameTestHelper h) {
		defaults(h);
		for (int z = 0; z < 4; z++) {
			h.setBlock(new BlockPos(0, 3, z), Blocks.STONE);
			h.setBlock(new BlockPos(1, 3, z), Blocks.STONE);
		}
		lavaPool(h, 2, 5, 1, 3);
		ServerPlayer p = facingPlusX(h, new Vec3(2.2, 4, 1.5)); // centre over x = 2 (air above lava), box on x = 1
		hold(p, 2);
		ActiveEvent ev = start(h, "butterfingers", p);
		Butterfingers.Toss toss = Butterfingers.plan(h.getLevel(), p);
		h.assertTrue(toss != null && toss.velocity().equals(Vec3.ZERO), "set down: " + toss);
		BlockPos under = BlockPos.containing(toss.from().x, toss.from().y - 0.5, toss.from().z);
		h.assertTrue(h.getLevel().getBlockState(under).is(Blocks.STONE), "over the stone, not over the edge: " + toss.from());
		h.assertTrue(Butterfingers.attempt(ev, p, true), "dropped");
		assertSurvives(h, p, ev, 2, () -> removeLava(h, 2, 5, 1, 3));
	}

	/** Overhang above a drop with no floor in reach (the void, a ravine): never tossed over it, set down on the block. */
	@GameTest
	public void noFloorAheadIsNeverATarget(GameTestHelper h) {
		defaults(h);
		for (int z = 0; z < 4; z++) {
			h.setBlock(new BlockPos(0, 4, z), Blocks.STONE);
			h.setBlock(new BlockPos(1, 4, z), Blocks.STONE);
		}
		ServerPlayer p = facingPlusX(h, new Vec3(1.5, 5, 1.5)); // nothing below x >= 2 down to relative y = 1
		hold(p, 1);
		ActiveEvent ev = start(h, "butterfingers", p);
		Butterfingers.Toss toss = Butterfingers.plan(h.getLevel(), p);
		h.assertTrue(toss != null && toss.velocity().equals(Vec3.ZERO), "set down, not tossed over the drop: " + toss);
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p);
		h.succeed();
	}

	/** Neither the flight nor the feet are safe (standing right over lava): nothing is dropped at all. */
	@GameTest
	public void nothingDroppedWhenNoSafePlace(GameTestHelper h) {
		defaults(h);
		lavaPool(h, 1, 4, 1, 3);
		ServerPlayer p = facingPlusX(h, new Vec3(2.5, 2, 2.5));
		hold(p, 3);
		ActiveEvent ev = start(h, "butterfingers", p);
		h.assertTrue(Butterfingers.plan(h.getLevel(), p) == null, "no safe place");
		h.assertFalse(Butterfingers.attempt(ev, p, true), "kept");
		h.assertValueEqual(p.getMainHandItem().getCount(), 3, "still in the hand");
		h.assertValueEqual(Butterfingers.dropsLeft(ev, p), Butterfingers.MAX_DROPS, "no drop used up");
		removeLava(h, 1, 4, 1, 3);
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void creativeIsNeverAffected(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("butterfingers"), p).isEmpty(), "refused for Creative");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void endsCleanly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "butterfingers", p);
		h.assertTrue(ev.hasBossBar(), "timed");
		ev.setRemainingTicks(3);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenExecute(() -> {
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "boss bar gone");
					h.assertTrue(manager(h).activeFor(p).isEmpty(), "nothing active");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
