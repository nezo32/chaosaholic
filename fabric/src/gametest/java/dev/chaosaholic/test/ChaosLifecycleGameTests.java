package dev.chaosaholic.test;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.Optional;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.helper.TempBlockStore;
import dev.chaosaholic.event.helper.TrackedNames;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Instance lifecycle and cleanup: boss bars, natural end, forced stop, logout, errors, instant events, and the
 * crash / unload safety nets (orphan entities, orphan names, leftover temporary blocks, unsaved owned entities).
 */
public class ChaosLifecycleGameTests {
	/** Boss bar with the event name while running, gone the tick the timer runs out; effects reverted. */
	@GameTest(maxTicks = 100)
	public void bossBarDuringAndGoneAfter(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = TestSupport.start(h, "speed_demon", p);
		h.assertTrue(ev.hasBossBar(), "timed event has a bar");
		h.assertTrue(ev.bossBarPlayers().contains(p), "bar shown to the player");
		String bar = EventManager.barName(ev).getString();
		h.assertTrue(bar.startsWith("Speed Demon — ") && bar.endsWith(" s"), "bar name (English fallback on the server): " + bar);
		ev.setRemainingTicks(5);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "expired"))
				.thenExecute(() -> {
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "bar removed");
					h.assertFalse(p.hasEffect(MobEffects.SPEED), "effect removed at the end");
					h.assertTrue(manager(h).activeFor(p).isEmpty(), "no active event");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** Instant events run in the same tick, get no boss bar and leave nothing active. */
	@GameTest
	public void instantEventHasNoBar(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h);
		int before = TestEvents.INSTANT_RUNS.get();
		ActiveEvent ev = TestSupport.start(h, "test_instant", p);
		h.assertValueEqual(TestEvents.INSTANT_RUNS.get(), before + 1, "ran once");
		h.assertFalse(ev.hasBossBar(), "no bar");
		h.assertTrue(ev.isStopped(), "already over");
		h.assertTrue(manager(h).activeFor(p).isEmpty(), "nothing active");
		TestSupport.start(h, "test_instant", p);
		h.assertValueEqual(TestEvents.INSTANT_RUNS.get(), before + 2, "instant events never extend: runs again");
		cleanup(h, p);
		h.succeed();
	}

	/** Forced stop reverts every tracker: owned entity gone, temporary block restored, names restored, glow removed. */
	@GameTest
	public void forcedStopCleansEverything(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 6.5));
		Pig pig = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 6.5));
		Pig far = mob(h, EntityTypes.PIG, new Vec3(3.5, 2, 0.5)); // 6 blocks north: replaced by a chicken
		BlockPos glass = p.blockPosition().above(3);
		ActiveEvent ev = TestSupport.start(h, "test_resources", p);
		h.assertValueEqual(ev.entities().count(), 2, "owned: zombie + chicken");
		Entity zombie = ev.entities().list().stream().filter(e -> e instanceof Zombie).findFirst().orElseThrow();
		h.assertTrue(zombie.entityTags().contains(Marks.OWNED_TAG), "owned tag");
		h.assertTrue(OwnedEntities.isUnsaved(zombie) && !zombie.shouldBeSaved(), "owned zombie is never saved");
		Entity chicken = ev.entities().list().stream().filter(e -> e instanceof Chicken).findFirst().orElseThrow();
		h.assertTrue(far.isRemoved(), "far pig replaced");
		h.assertTrue(chicken.shouldBeSaved(), "replacement is saved (with the original)");
		h.assertTrue(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "temporary glass");
		h.assertTrue(pig.hasCustomName(), "pig renamed");
		h.assertTrue(pig.hasEffect(MobEffects.GLOWING), "pig glows");

		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(zombie.isRemoved(), "zombie removed");
		h.assertTrue(chicken.isRemoved(), "chicken removed");
		h.assertFalse(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "glass restored");
		h.assertFalse(pig.hasCustomName(), "name restored");
		h.assertFalse(pig.hasAttached(Marks.NAME), "name mark removed");
		h.assertFalse(pig.hasEffect(MobEffects.GLOWING), "glow removed");
		var restored = h.getLevel().getEntitiesOfClass(Pig.class, new AABB(chicken.position(), chicken.position()).inflate(1.5),
				e -> e.getUUID().equals(far.getUUID()));
		h.assertValueEqual(restored.size(), 1, "original pig restored with its uuid");
		h.assertValueEqual(TempBlockStore.get(h.getLevel().getServer()).count(ev.uuid()), 0, "store empty for the instance");
		restored.forEach(Entity::discard);
		pig.discard();
		cleanup(h, p);
		h.succeed();
	}

	/** Logout: the player is removed (hook sees LOGOUT), effects revert, the instance ends and cleans up. */
	@GameTest(maxTicks = 60)
	public void logoutEndsPlayerEvents(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		ActiveEvent speed = TestSupport.start(h, "speed_demon", p);
		ActiveEvent res = TestSupport.start(h, "test_resources", p);
		int logouts = TestEvents.REMOVED_BY_LOGOUT.get();
		BlockPos glass = p.blockPosition().above(3);
		leave(h, p);
		h.assertValueEqual(TestEvents.REMOVED_BY_LOGOUT.get(), logouts + 1, "onPlayerRemoved(LOGOUT)");
		h.assertFalse(p.hasEffect(MobEffects.SPEED), "effect reverted on logout");
		h.assertTrue(speed.bossBarPlayers().isEmpty(), "bar removed on logout");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(speed.isStopped() && res.isStopped(), "instances ended"))
				.thenExecute(() -> {
					h.assertFalse(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "glass restored");
					h.assertValueEqual(res.entities().count(), 0, "owned entities gone");
				})
				.thenSucceed();
	}

	/** An exception in a hook stops only that instance (ERROR) and still runs the full cleanup. */
	@GameTest(maxTicks = 60)
	public void failingEventIsStoppedAndCleaned(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(5.5, 2, 5.5));
		BlockPos glass = p.blockPosition().above(4);
		int stops = TestEvents.BOOM_STOPS.get();
		ActiveEvent boom = TestSupport.start(h, "test_boom", p);
		ActiveEvent speed = TestSupport.start(h, "speed_demon", p);
		h.assertTrue(p.hasEffect(MobEffects.SLOWNESS), "slowness");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(boom.isStopped(), "boom stopped"))
				.thenExecute(() -> {
					h.assertValueEqual(TestEvents.BOOM_STOPS.get(), stops + 1, "onStop(ERROR)");
					h.assertFalse(p.hasEffect(MobEffects.SLOWNESS), "slowness reverted");
					h.assertFalse(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "glass restored");
					h.assertFalse(speed.isStopped(), "other events keep running");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** Crash safety: an owned entity whose owner is not running is removed when it loads (next tick). */
	@GameTest(maxTicks = 40)
	public void orphanOwnedEntityIsRemovedOnLoad(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(2.5, 2, 2.5));
		zombie.addTag(Marks.OWNED_TAG);
		zombie.setAttached(Marks.OWNER, new Marks.OwnerMark(UUID.randomUUID(), Optional.empty()));
		Pig named = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 2.5));
		named.setCustomName(Component.literal("Dinnerbone"));
		named.setAttached(Marks.NAME, new Marks.NameMark(UUID.randomUUID(), Optional.empty(), false));
		OwnedEntities.onEntityLoad(zombie, level); // what ENTITY_LOAD does after a restart
		TrackedNames.onEntityLoad(named, level);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(zombie.isRemoved(), "orphan removed"))
				.thenExecute(() -> {
					h.assertFalse(named.hasCustomName(), "orphan name restored");
					h.assertFalse(named.hasAttached(Marks.NAME), "mark removed");
					named.discard();
				})
				.thenSucceed();
	}

	/** Crash safety: a replacement whose owner is gone turns back into the original when loaded. */
	@GameTest(maxTicks = 40)
	public void orphanReplacementIsRestoredOnLoad(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 6.5));
		Pig far = mob(h, EntityTypes.PIG, new Vec3(3.5, 2, 0.5));
		UUID pigId = far.getUUID();
		ActiveEvent ev = TestSupport.start(h, "test_resources", p);
		Entity chicken = ev.entities().list().stream().filter(e -> e instanceof Chicken).findFirst().orElseThrow();
		// simulate "saved, server crashed, loaded again": forget the instance, keep the chicken with its mark
		ev.entities().forget(chicken);
		ev.entities().revertAll();
		manager(h).stop(ev, StopReason.FORCED);
		h.assertFalse(chicken.isRemoved(), "chicken still there (forgotten)");
		OwnedEntities.onEntityLoad(chicken, h.getLevel());
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(chicken.isRemoved(), "orphan replacement reverted"))
				.thenExecute(() -> {
					var pigs = h.getLevel().getEntitiesOfClass(Pig.class, new AABB(chicken.position(), chicken.position()).inflate(1.5),
							e -> e.getUUID().equals(pigId));
					h.assertValueEqual(pigs.size(), 1, "original back");
					pigs.forEach(Entity::discard);
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** Crash safety: temporary blocks left in the store (no running owner) are restored on the next start. */
	@GameTest
	public void leftoverTempBlocksRestoredOnStart(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		BlockPos glass = p.blockPosition().above(3);
		ActiveEvent ev = TestSupport.start(h, "test_resources", p);
		h.assertTrue(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "glass placed");
		h.assertValueEqual(TempBlockStore.get(h.getLevel().getServer()).count(ev.uuid()), 1, "recorded");
		// the store is what survives a crash: restoreAll is what SERVER_STARTED runs
		TempBlockStore.restoreAll(h.getLevel().getServer());
		h.assertFalse(h.getLevel().getBlockState(glass).is(Blocks.GLASS), "restored from the store");
		h.assertValueEqual(TempBlockStore.get(h.getLevel().getServer()).count(ev.uuid()), 0, "store cleared");
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p);
		h.succeed();
	}

	/** Chunk unload: the trackers forget the entity (its transient modifiers are not saved, owned ones are never saved). */
	@GameTest
	public void unloadForgetsEntities(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		ActiveEvent ev = TestSupport.start(h, "test_resources", p);
		Entity zombie = ev.entities().list().getFirst();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(zombie, h.getLevel());
		h.assertFalse(ev.entities().owns(zombie), "forgotten on unload");
		zombie.discard();
		Zombie fresh = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
		h.assertTrue(fresh != null && !OwnedEntities.isUnsaved(fresh), "plain entities are saved");
		cleanup(h, p);
		h.succeed();
	}

	/** adopt(): an entity that added itself (e.g. FallingBlockEntity.fall) becomes owned, unsaved and removed at the end. */
	@GameTest
	public void adoptedEntityIsOwned(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = TestSupport.start(h, "test_marker", p);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(3.5, 2, 3.5));
		h.assertTrue(ev.entities().adopt(zombie), "adopted");
		h.assertTrue(ev.entities().owns(zombie) && OwnedEntities.isUnsaved(zombie) && !zombie.shouldBeSaved(), "owned, never saved");
		h.assertTrue(zombie.entityTags().contains(Marks.OWNED_TAG), "tag");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(zombie.isRemoved(), "removed at the end");
		cleanup(h, p);
		h.succeed();
	}
}
