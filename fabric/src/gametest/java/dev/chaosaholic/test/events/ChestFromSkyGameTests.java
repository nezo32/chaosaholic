package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.impl.ChestFromSky;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * chest_from_sky: an instant event that places a loot-table chest on a free (air) safe spot 2-5 blocks from the
 * player. The chest is the reward and stays; the tests remove it themselves.
 */
public class ChestFromSkyGameTests {
	/** Stone floor under the whole structure at relative y = 1, so the spots inside it are predictable. */
	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		}
	}

	/**
	 * Chests within reach of the event around {@code center} (Spots: same height ±6, or the surface within 10 blocks).
	 * Outside the structure the spot may be on uncontrolled ground; the test runs in one tick and removes the chests
	 * in that same tick, so nothing leaks into neighbouring tests.
	 */
	private static List<BlockPos> chests(ServerLevel level, BlockPos center) {
		List<BlockPos> found = new ArrayList<>();
		int r = ChestFromSky.MAX_DISTANCE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -11, -r), center.offset(r, 11, r))) {
			if (level.getBlockState(pos).is(Blocks.CHEST)) found.add(pos.immutable());
		}
		return found;
	}

	@GameTest
	public void chestLandsNearbyAndStays(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerLevel level = h.getLevel();
		ServerPlayer p = survivalPlayer(h, new Vec3(4.5, 2, 4.5));
		BlockPos center = p.blockPosition();
		for (int i = 0; i < 3; i++) {
			ActiveEvent ev = start(h, ChestFromSky.ID, p);
			h.assertTrue(ev.isStopped() && !ev.hasBossBar(), "instant: done in the same tick, no boss bar");
		}
		List<BlockPos> chests = chests(level, center);
		h.assertValueEqual(chests.size(), 3, "three chests, none placed over another: " + chests);
		for (BlockPos pos : chests) {
			int dx = pos.getX() - center.getX();
			int dz = pos.getZ() - center.getZ();
			int d2 = dx * dx + dz * dz;
			h.assertTrue(d2 >= ChestFromSky.MIN_DISTANCE * ChestFromSky.MIN_DISTANCE
					&& d2 <= ChestFromSky.MAX_DISTANCE * ChestFromSky.MAX_DISTANCE, "distance " + pos);
			h.assertTrue(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP), "on a floor " + pos);
			h.assertTrue(level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null
					&& ChestFromSky.LOOT_TABLES.contains(chest.getLootTable()), "loot table at " + pos);
			BlockState state = level.getBlockState(pos);
			Vec3 toPlayer = p.position().subtract(Vec3.atCenterOf(pos));
			h.assertTrue(state.getValue(ChestBlock.FACING).getStepX() * toPlayer.x + state.getValue(ChestBlock.FACING).getStepZ() * toPlayer.z > 0,
					"faces the player " + pos);
		}
		h.assertTrue(manager(h).activeFor(p).isEmpty(), "nothing running");
		for (BlockPos pos : chests) level.removeBlock(pos, false); // the chest is permanent: the test cleans its own
		cleanup(h, p);
		h.succeed();
	}

	/**
	 * Review finding: indoors the chest used to land on the roof (Spots scanned the column top-down). A closed stone
	 * room (floor y = 1, walls, ceiling y = 4): the spot is always in the room at the player's height, which the
	 * player sees, never on the roof.
	 */
	@GameTest
	public void indoorsTheChestLandsInTheRoom(GameTestHelper h) {
		defaults(h);
		floor(h);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 4, z), Blocks.STONE); // ceiling
				if (x == 0 || x == 7 || z == 0 || z == 7) {
					h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
					h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				}
			}
		}
		ServerLevel level = h.getLevel();
		ServerPlayer p = survivalPlayer(h, new Vec3(4.5, 2, 4.5));
		BlockPos center = p.blockPosition();
		BlockPos column = h.absolutePos(new BlockPos(6, 2, 4));
		Optional<Vec3> feet = Spots.column(level, column.getX(), center.getY(), column.getZ(), EntityTypes.PLAYER);
		h.assertTrue(feet.isPresent() && feet.get().y == center.getY(), "nearest height first, not the roof: " + feet);
		for (int i = 0; i < 10; i++) {
			Optional<BlockPos> spot = ChestFromSky.findSpot(level, center, level.getRandom());
			h.assertTrue(spot.isPresent() && spot.get().getY() == center.getY(), "in the room: " + spot);
		}
		ActiveEvent ev = start(h, ChestFromSky.ID, p);
		h.assertTrue(ev.isStopped(), "instant");
		List<BlockPos> chests = chests(level, center);
		h.assertValueEqual(chests.size(), 1, "one chest");
		boolean inRoom = false;
		for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) inRoom |= h.absolutePos(new BlockPos(x, 2, z)).equals(chests.getFirst());
		h.assertTrue(inRoom, "chest inside the room: " + chests.getFirst());
		for (BlockPos pos : chests) level.removeBlock(pos, false);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void refusedWithoutSafeSpot(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(4.5, 2, 4.5));
		p.snapTo(p.getX(), h.getLevel().getMaxY() + 20, p.getZ(), 0.0F, 0.0F); // high above the build limit: no floor
		h.assertTrue(manager(h).trigger(event(ChestFromSky.ID), p).isEmpty(), "no spot: refused (rerolled)");
		cleanup(h, p);
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event(ChestFromSky.ID), creative).isEmpty(), "creative: refused");
		cleanup(h, creative);
		h.succeed();
	}

	@GameTest
	public void neverReplacesBlocksOrLandsOnEntities(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerLevel level = h.getLevel();
		h.setBlock(new BlockPos(6, 2, 6), Blocks.BARREL);
		h.setBlock(new BlockPos(6, 2, 2), Blocks.SNOW);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(2.5, 2, 6.5));
		h.assertFalse(ChestFromSky.canPlace(level, h.absolutePos(new BlockPos(6, 2, 6))), "container");
		h.assertFalse(ChestFromSky.canPlace(level, h.absolutePos(new BlockPos(6, 2, 2))), "snow layer (replaceable, not air)");
		h.assertFalse(ChestFromSky.canPlace(level, h.absolutePos(new BlockPos(2, 2, 6))), "entity");
		h.assertTrue(ChestFromSky.canPlace(level, h.absolutePos(new BlockPos(2, 2, 2))), "free air");
		zombie.discard();
		h.succeed();
	}
}
