package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.active;
import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
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
import net.minecraft.world.level.GameType;
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
