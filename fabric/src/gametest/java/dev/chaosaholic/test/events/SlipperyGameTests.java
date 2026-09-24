package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.Slippery;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** slippery: friction modifier 0.05 (ice) on the player and up to 16 nearby mobs; reverted at the end / on logout. */
public class SlipperyGameTests {
	private static final double ICE = 0.05;

	private static double friction(LivingEntity e) {
		return e.getAttributeValue(Attributes.FRICTION_MODIFIER);
	}

	@GameTest
	public void iceForPlayerAndNearbyMobs(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie near = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		Zombie far = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 1.5 + Slippery.RADIUS + 3));
		ActiveEvent ev = start(h, "slippery", p);
		h.assertTrue(Math.abs(friction(p) - ICE) < 1.0E-6, "player on ice: " + friction(p));
		h.assertTrue(Math.abs(friction(near) - ICE) < 1.0E-6, "near zombie on ice: " + friction(near));
		h.assertValueEqual(friction(far), 1.0, "far zombie untouched");
		ActiveEvent again = start(h, "slippery", p);
		h.assertTrue(ev == again, "extended, not duplicated");
		h.assertTrue(Math.abs(friction(p) - ICE) < 1.0E-6, "one modifier after the extension");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(friction(p), 1.0, "player restored");
		h.assertValueEqual(friction(near), 1.0, "zombie restored");
		near.discard();
		far.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void mobCap(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		List<Zombie> zombies = new ArrayList<>();
		for (int i = 0; i < Slippery.MAX_MOBS + 4; i++) {
			zombies.add(mob(h, EntityTypes.ZOMBIE, new Vec3(1.5 + i % 5, 2, 1.5 + i / 5)));
		}
		ActiveEvent ev = start(h, "slippery", p);
		long slippery = zombies.stream().filter(z -> friction(z) < 1.0).count();
		h.assertValueEqual((int) slippery, Slippery.MAX_MOBS, "capped mobs");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(zombies.stream().allMatch(z -> friction(z) == 1.0), "all restored");
		zombies.forEach(Zombie::discard);
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void logoutRevertsPlayer(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "slippery", p);
		leave(h, p);
		h.assertValueEqual(friction(p), 1.0, "restored on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}
}
