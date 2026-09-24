package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.active;
import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.impl.Swap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * swap (instant): the player and a fair-game mob on safe ground trade places, rotations kept, fall distance reset;
 * refused when no mob can be swapped safely. The gametest structure is empty air: tests lay their own stone floor
 * at relative y = 1 (only mobs standing on ground are candidates, so neighbour tests' floating mobs never are).
 */
public class SwapGameTests {
	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		}
	}

	private static boolean near(Vec3 a, Vec3 b) {
		return a.distanceTo(b) < 1.0E-6;
	}

	@GameTest
	public void swapsPlayerAndMob(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		p.setYRot(45.0F);
		p.setXRot(10.0F);
		p.fallDistance = 12.0;
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(5.5, 2, 5.5));
		z.setYRot(90.0F);
		Vec3 playerBefore = p.position();
		Vec3 mobBefore = z.position();
		h.assertTrue(Swap.candidates(h.getLevel(), p).contains(z), "zombie is a candidate");
		h.assertTrue(Swap.swap(h.getLevel(), p, z), "swapped");
		h.assertTrue(near(p.position(), mobBefore), "player at the zombie's spot: " + p.position());
		h.assertTrue(near(z.position(), playerBefore), "zombie at the player's spot: " + z.position());
		h.assertValueEqual(p.getYRot(), 45.0F, "player yaw kept");
		h.assertValueEqual(p.getXRot(), 10.0F, "player pitch kept");
		h.assertValueEqual(z.getYRot(), 90.0F, "mob yaw kept");
		h.assertValueEqual(p.fallDistance, 0.0, "fall distance reset");
		z.discard();
		cleanup(h, p);
		h.succeed();
	}

	/** Through the event: the player lands where some candidate stood and that mob now stands where the player was. */
	@GameTest
	public void eventSwapsWithACandidate(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(5.5, 2, 5.5));
		Vec3 playerBefore = p.position();
		List<LivingEntity> candidates = Swap.candidates(h.getLevel(), p);
		List<Vec3> before = candidates.stream().map(LivingEntity::position).toList();
		h.assertTrue(manager(h).trigger(event("swap"), p).isPresent(), "started");
		h.assertTrue(active(h, p).isEmpty(), "instant: nothing left running");
		boolean found = false;
		for (int i = 0; i < candidates.size(); i++) {
			LivingEntity c = candidates.get(i);
			if (near(c.position(), playerBefore) && near(p.position(), before.get(i))) found = true;
		}
		h.assertTrue(found, "player and one candidate traded places");
		z.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void onlySafeFairGameMobsAreCandidates(GameTestHelper h) {
		defaults(h);
		floor(h);
		h.setBlock(new BlockPos(6, 2, 1), Blocks.WATER);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie floating = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 5, 4.5)); // no ground below
		Zombie swimming = mob(h, EntityTypes.ZOMBIE, new Vec3(6.5, 2, 1.5)); // in water
		Zombie named = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 3.5));
		named.setCustomName(Component.literal("Bob"));
		Warden boss = mob(h, EntityTypes.WARDEN, new Vec3(5.5, 2, 5.5));
		Zombie ok = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 6.5));
		List<LivingEntity> c = Swap.candidates(h.getLevel(), p);
		h.assertFalse(c.contains(floating), "floating mob refused");
		h.assertFalse(c.contains(swimming), "mob in water refused");
		h.assertFalse(c.contains(named), "named mob refused");
		h.assertFalse(c.contains(boss), "boss refused");
		h.assertTrue(c.contains(ok), "safe mob accepted");
		h.assertFalse(Swap.canSwap(h.getLevel(), p, floating), "no swap with the floating mob");
		for (LivingEntity e : List.of(floating, swimming, named, boss, ok)) e.discard();
		h.setBlock(new BlockPos(6, 2, 1), Blocks.AIR); // synchronous test: the water never gets to flow
		cleanup(h, p);
		h.succeed();
	}

	/** A player in mid-air (nowhere a mob could stand) or riding: canStart false, nothing moves. */
	@GameTest
	public void refusedWithoutSafeSwap(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 6, 1.5));
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(5.5, 2, 5.5));
		Vec3 mobBefore = z.position();
		Vec3 playerBefore = p.position();
		h.assertTrue(Swap.candidates(h.getLevel(), p).isEmpty(), "no candidate while the player floats");
		h.assertTrue(manager(h).trigger(event("swap"), p).isEmpty(), "refused");
		h.assertTrue(near(p.position(), playerBefore) && near(z.position(), mobBefore), "nothing moved");
		z.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void creativeNeverSwapped(GameTestHelper h) {
		defaults(h);
		floor(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		p.setGameMode(GameType.CREATIVE);
		Zombie z = mob(h, EntityTypes.ZOMBIE, new Vec3(5.5, 2, 5.5));
		Vec3 before = p.position();
		h.assertTrue(manager(h).trigger(event("swap"), p).isEmpty(), "creative refused");
		h.assertTrue(near(p.position(), before), "not moved");
		z.discard();
		cleanup(h, p);
		h.succeed();
	}
}
