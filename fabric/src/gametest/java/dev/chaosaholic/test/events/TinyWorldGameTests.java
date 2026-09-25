package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.scale;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.TinyWorld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** tiny_world: player + mobs within 12 blocks at half size; reverted at the end, on logout, and never saved. */
public class TinyWorldGameTests {
	@GameTest
	public void shrinksPlayerAndNearbyMobs(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie near = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		Zombie far = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 1.5 + TinyWorld.RADIUS + 3));
		ActiveEvent ev = start(h, "tiny_world", p);
		h.assertValueEqual(scale(p), 0.5, "player scale");
		h.assertValueEqual(scale(near), 0.5, "near zombie scale");
		h.assertValueEqual(scale(far), 1.0, "far zombie untouched");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(scale(p), 1.0, "player restored");
		h.assertValueEqual(scale(near), 1.0, "zombie restored");
		near.discard();
		far.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void logoutRevertsPlayerThenEventEnds(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie near = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		ActiveEvent ev = start(h, "tiny_world", p);
		leave(h, p);
		h.assertValueEqual(scale(p), 1.0, "player restored on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenExecute(() -> {
					h.assertValueEqual(scale(near), 1.0, "zombie restored at the end");
					near.discard();
				})
				.thenSucceed();
	}

	/** Transient modifiers are not in the saved data (chunk unload / crash leave nothing); unload makes the tracker forget. */
	@GameTest
	public void neverSavedAndForgottenOnUnload(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie near = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 1.5));
		ActiveEvent ev = start(h, "tiny_world", p);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
		near.saveWithoutId(out);
		CompoundTag saved = out.buildResult();
		h.assertFalse(saved.toString().contains("chaosaholic:event/"), "modifier saved: " + saved.get("attributes"));
		ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(near, h.getLevel());
		h.assertFalse(ev.modifiers().tracks(near), "forgotten on unload");
		near.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void extensionKeepsOneModifier(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ActiveEvent ev = start(h, "tiny_world", p);
		ActiveEvent again = start(h, "tiny_world", p);
		h.assertTrue(ev == again, "extended, not duplicated");
		h.assertValueEqual(scale(p), 0.5, "still half, not a quarter");
		cleanup(h, p);
		h.assertValueEqual(scale(p), 1.0, "restored");
		h.succeed();
	}
}
