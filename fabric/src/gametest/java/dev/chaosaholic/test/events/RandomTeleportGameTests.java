package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.active;
import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.Optional;

import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.impl.RandomTeleport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * random_teleport (instant): 8-24 blocks away on a safe spot, rotation kept, fall distance reset; refused where no
 * safe spot exists. The "spot found" tests stand the mock player on the first ground found scanning down from the
 * structure's bottom (the gametest world is a flat, loaded, hazard-free plain with the test floors on it).
 */
public class RandomTeleportGameTests {
	/** The first safe standing position scanning down from the structure's bottom layer. */
	private static Vec3 ground(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos origin = h.absolutePos(new BlockPos(3, 0, 3));
		for (int y = origin.getY(); y > level.getMinY(); y--) {
			BlockPos pos = new BlockPos(origin.getX(), y, origin.getZ());
			if (Spots.isSafe(level, pos, EntityTypes.PLAYER)) return Vec3.atBottomCenterOf(pos);
		}
		throw new AssertionError("no ground below the test");
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@GameTest
	public void teleportsToASafeSpotInRange(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Vec3 ground = ground(h);
		for (int i = 0; i < 5; i++) { // random: several rolls
			p.snapTo(ground.x, ground.y, ground.z, 30.0F, 15.0F);
			p.fallDistance = 20.0;
			h.assertTrue(manager(h).trigger(event("random_teleport"), p).isPresent(), "started");
			h.assertTrue(active(h, p).isEmpty(), "instant: nothing left running");
			double d = horizontal(p.position(), ground);
			h.assertTrue(d >= RandomTeleport.MIN_DISTANCE && d <= RandomTeleport.MAX_DISTANCE, "distance in range: " + d);
			h.assertTrue(p.level() == h.getLevel(), "same dimension");
			h.assertTrue(Spots.isSafe(h.getLevel(), p.blockPosition(), EntityTypes.PLAYER), "safe spot: " + p.position());
			h.assertValueEqual(p.fallDistance, 0.0, "no fall damage carried over");
			h.assertValueEqual(p.getYRot(), 30.0F, "yaw kept");
			h.assertValueEqual(p.getXRot(), 15.0F, "pitch kept");
		}
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void findSpotStaysInRange(GameTestHelper h) {
		defaults(h);
		Vec3 ground = ground(h);
		for (int i = 0; i < 20; i++) {
			Optional<Vec3> spot = RandomTeleport.findSpot(h.getLevel(), ground, h.getLevel().getRandom());
			h.assertTrue(spot.isPresent(), "spot on the flat plain");
			double d = horizontal(spot.get(), ground);
			h.assertTrue(d >= RandomTeleport.MIN_DISTANCE && d <= RandomTeleport.MAX_DISTANCE, "distance in range: " + d);
			h.assertTrue(h.getLevel().getWorldBorder().isWithinBounds(spot.get()), "inside the border");
		}
		h.succeed();
	}

	/** Above the build height there is nothing to stand on within reach: refused, the player stays put. */
	@GameTest
	public void refusedWithoutSafeSpot(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Vec3 sky = new Vec3(p.getX(), h.getLevel().getMaxY() + 20, p.getZ());
		p.snapTo(sky.x, sky.y, sky.z, 0.0F, 0.0F);
		h.assertTrue(manager(h).trigger(event("random_teleport"), p).isEmpty(), "refused");
		h.assertTrue(p.position().distanceTo(sky) < 1.0E-6, "not moved");
		cleanup(h, p);
		h.succeed();
	}

	/**
	 * Under the open sky the surface spot of a column wins over a cave below it; without sky the height nearest to the
	 * player wins. Built in the structure: a stone floor (y = 1) and roof (y = 4) make a cave at y = 2; the surface of
	 * that column is the top of the structure's barrier ceiling (y = 9). The player is 10 blocks away at cave height.
	 */
	@GameTest
	public void underTheSkyTheSurfaceWins(GameTestHelper h) {
		defaults(h);
		ServerLevel level = h.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				if (x >= 4 && z >= 4) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
			}
		}
		BlockPos cave = h.absolutePos(new BlockPos(6, 2, 6));
		BlockPos top = h.absolutePos(new BlockPos(6, 9, 6));
		Vec3 from = Vec3.atBottomCenterOf(cave).add(10.0, 0.0, 0.0);
		Optional<Vec3> withSky = RandomTeleport.spotIn(level, from, cave.getX(), cave.getZ(), true);
		Optional<Vec3> withoutSky = RandomTeleport.spotIn(level, from, cave.getX(), cave.getZ(), false);
		h.assertTrue(withSky.isPresent() && BlockPos.containing(withSky.get()).equals(top), "sky: the surface " + withSky);
		h.assertTrue(withoutSky.isPresent() && BlockPos.containing(withoutSky.get()).equals(cave), "no sky: the cave " + withoutSky);
		h.succeed();
	}

	/**
	 * Review finding: a sealed 2-block pocket inside stone passed Spots (floor, room for a player). It is never a
	 * destination now: at least {@link Spots#MIN_OPEN_CELLS} connected free blocks are needed. Built inside the test
	 * structure and checked with the destination rule directly (a real run jumps 8-24 blocks, out of the structure).
	 */
	@GameTest
	public void sealedPocketIsNeverADestination(GameTestHelper h) {
		defaults(h);
		ServerLevel level = h.getLevel();
		for (int x = 5; x <= 7; x++) for (int z = 5; z <= 7; z++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
		h.setBlock(new BlockPos(6, 3, 6), Blocks.AIR);
		h.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
		BlockPos pocket = h.absolutePos(new BlockPos(6, 3, 6));
		Vec3 to = Vec3.atBottomCenterOf(pocket);
		Vec3 from = to.add(-10.0, 0.0, 0.0);
		h.assertTrue(Spots.isSafe(level, pocket, EntityTypes.PLAYER), "the pocket itself looks safe to Spots");
		h.assertFalse(Spots.isOpen(level, pocket), "but it is sealed");
		h.assertFalse(RandomTeleport.accepts(level, from, to), "never a destination");
		h.setBlock(new BlockPos(5, 3, 6), Blocks.AIR); // opened to the rest of the structure: open space
		h.assertTrue(Spots.isOpen(level, pocket), "open now");
		h.assertTrue(RandomTeleport.accepts(level, from, to), "accepted once open");
		h.succeed();
	}

	/** Water in the upper half of the player box (not only at the feet) refuses the spot. */
	@GameTest
	public void liquidInTheBoxIsNeverADestination(GameTestHelper h) {
		defaults(h);
		ServerLevel level = h.getLevel();
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		BlockPos feet = h.absolutePos(new BlockPos(3, 2, 3));
		Vec3 to = Vec3.atBottomCenterOf(feet);
		Vec3 from = to.add(0.0, 0.0, -10.0);
		h.assertTrue(RandomTeleport.accepts(level, from, to), "dry spot accepted");
		h.setBlock(new BlockPos(3, 3, 3), Blocks.WATER); // head height; removed again in this same tick
		h.assertFalse(RandomTeleport.accepts(level, from, to), "water at head height refused");
		h.setBlock(new BlockPos(3, 3, 3), Blocks.AIR);
		h.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);
		h.assertFalse(Spots.isSafe(level, feet, EntityTypes.PLAYER), "water at the feet refused");
		h.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		h.succeed();
	}

	/** A riding player is dismounted and teleported; the mount stays where it was. */
	@GameTest
	public void ridingPlayerIsDismounted(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Vec3 ground = ground(h);
		Pig pig = mob(h, EntityTypes.PIG, new Vec3(3.5, 2, 3.5));
		pig.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
		p.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
		h.assertTrue(p.startRiding(pig, true, false), "riding");
		h.assertTrue(manager(h).trigger(event("random_teleport"), p).isPresent(), "started");
		h.assertFalse(p.isPassenger(), "dismounted");
		h.assertTrue(horizontal(p.position(), ground) >= RandomTeleport.MIN_DISTANCE, "teleported: " + p.position());
		h.assertTrue(pig.position().distanceTo(ground) < 1.0E-6, "the pig stays");
		pig.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void creativeNeverTeleported(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Vec3 ground = ground(h);
		p.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
		p.setGameMode(GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("random_teleport"), p).isEmpty(), "creative refused");
		h.assertTrue(p.position().distanceTo(ground) < 1.0E-6, "not moved");
		cleanup(h, p);
		h.succeed();
	}
}
