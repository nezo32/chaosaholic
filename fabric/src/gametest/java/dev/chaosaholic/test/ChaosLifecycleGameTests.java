package dev.chaosaholic.test;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.scale;
import static dev.chaosaholic.test.TestSupport.server;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Area;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.helper.TempBlockStore;
import dev.chaosaholic.event.helper.TrackedNames;
import dev.chaosaholic.mixin.MobEffectInstanceAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
		// under a minute "42 s", from a minute "1:00" (speed_demon rolls 30-60 s)
		h.assertTrue(bar.startsWith("Speed Demon — ") && (bar.endsWith(" s") || bar.matches(".* \\d+:\\d\\d")),
				"bar name (English fallback on the server): " + bar);
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

	/**
	 * Crash safety: temporary blocks left in the store (no running owner) are restored on the next start.
	 * restoreAll touches every running instance's blocks: global-state environment.
	 */
	@GameTest(environment = TestSupport.GLOBAL_STATE)
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

	/**
	 * One owner's blocks come back through TempBlockStore#restore without touching others; the check is by block
	 * type, so a fence whose connections changed (a neighbour was built) is still restored.
	 */
	@GameTest
	public void tempBlocksRestoredByTypeAndOwner(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ActiveEvent mine = TestSupport.start(h, "test_marker", p);
		ActiveEvent other = TestSupport.start(h, "feather_fall", p);
		BlockPos fence = h.absolutePos(new BlockPos(1, 5, 1));
		BlockPos kept = h.absolutePos(new BlockPos(3, 5, 1));
		h.assertTrue(mine.blocks().set(fence, Blocks.OAK_FENCE.defaultBlockState()), "fence placed");
		h.assertTrue(other.blocks().set(kept, Blocks.GLASS.defaultBlockState()), "other instance's glass");
		h.getLevel().setBlockAndUpdate(fence.east(), Blocks.OAK_FENCE.defaultBlockState()); // a player builds next to it
		h.assertFalse(h.getLevel().getBlockState(fence) == Blocks.OAK_FENCE.defaultBlockState(), "connections changed");
		TempBlockStore.get(server(h)).restore(server(h), mine.uuid());
		h.assertTrue(h.getLevel().getBlockState(fence).isAir(), "fence restored despite new connections");
		h.assertTrue(h.getLevel().getBlockState(kept).is(Blocks.GLASS), "other owner untouched");
		h.assertValueEqual(TempBlockStore.get(server(h)).count(other.uuid()), 1, "other owner still recorded");
		h.getLevel().setBlockAndUpdate(fence.east(), Blocks.AIR.defaultBlockState());
		cleanup(h, p);
		h.assertTrue(h.getLevel().getBlockState(kept).isAir(), "other restored at its end");
		h.succeed();
	}

	/** Vanilla keeps a weaker, longer effect hidden under ours: it must come back when the event ends. */
	@GameTest
	public void hiddenWeakerEffectSurvivesTheEvent(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 9600, 0)); // Swiftness potion (8 min)
		ActiveEvent ev = TestSupport.start(h, "speed_demon", p);
		h.assertValueEqual(p.getEffect(MobEffects.SPEED).getAmplifier(), 2, "Speed III on top");
		manager(h).stop(ev, StopReason.FORCED);
		MobEffectInstance after = p.getEffect(MobEffects.SPEED);
		h.assertTrue(after != null && after.getAmplifier() == 0, "Speed I back: " + after);
		h.assertTrue(after.getDuration() > 9000 && after.getDuration() <= 9600, "remaining duration kept: " + after.getDuration());
		// ours hidden under a stronger foreign effect is unlinked, never resurfaces
		ActiveEvent again = TestSupport.start(h, "speed_demon", p); // Speed III over Speed I
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 3)); // short Speed IV on top: ours becomes hidden
		manager(h).stop(again, StopReason.FORCED);
		MobEffectInstance top = p.getEffect(MobEffects.SPEED);
		h.assertTrue(top != null && top.getAmplifier() == 3, "foreign Speed IV left alone: " + top);
		MobEffectInstance below = ((MobEffectInstanceAccessor) top).chaosaholic$getHiddenEffect();
		h.assertTrue(below != null && below.getAmplifier() == 0, "ours unlinked, the potion still below: " + below);
		cleanup(h, p);
		h.succeed();
	}

	/**
	 * Trackers refuse players the instance may not change (Creative, Spectator); an eligible bystander may be changed
	 * (glow_party) and is reverted when it logs out, even though it is not an affected player.
	 */
	@GameTest
	public void trackersRefuseIneligiblePlayers(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer creative = survivalPlayer(h, new Vec3(2.5, 2, 1.5));
		creative.setGameMode(GameType.CREATIVE);
		ServerPlayer bystander = survivalPlayer(h, new Vec3(3.5, 2, 1.5));
		ActiveEvent ev = TestSupport.start(h, "test_marker", p);
		h.assertFalse(ev.effects().give(creative, MobEffects.GLOWING, 0), "creative refused (effect)");
		h.assertFalse(creative.hasEffect(MobEffects.GLOWING), "creative untouched");
		h.assertFalse(ev.modifiers().add(creative, Attributes.SCALE, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), "creative refused (modifier)");
		h.assertTrue(ev.effects().give(bystander, MobEffects.GLOWING, 0), "eligible bystander accepted");
		h.assertTrue(ev.modifiers().add(bystander, Attributes.SCALE, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), "bystander modifier");
		leave(h, bystander);
		h.assertFalse(bystander.hasEffect(MobEffects.GLOWING), "bystander reverted on logout (not saved with it)");
		h.assertFalse(ev.effects().tracks(bystander) || ev.modifiers().tracks(bystander), "forgotten");
		h.assertTrue(scale(bystander) > 0.99, "modifier removed");
		cleanup(h, p, creative);
		h.succeed();
	}

	/** Dimension change: the player leaves the instance (boss bar gone, effects reverted); bystanders are reverted too. */
	@GameTest
	public void dimensionChangeRemovesThePlayer(GameTestHelper h) {
		defaults(h);
		ServerLevel nether = server(h).getLevel(Level.NETHER);
		h.assertTrue(nether != null, "the gametest world (flat, all dimensions) has a nether");
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer bystander = survivalPlayer(h, new Vec3(3.5, 2, 1.5));
		ActiveEvent ev = TestSupport.start(h, "speed_demon", p);
		ActiveEvent marker = TestSupport.start(h, "test_marker", p);
		h.assertTrue(marker.effects().give(bystander, MobEffects.SPEED, 0), "bystander changed");
		h.assertTrue(p.hasEffect(MobEffects.SPEED) && ev.bossBarPlayers().contains(p), "running");
		p.teleportTo(nether, 0.5, 100, 0.5, Set.of(), 0.0F, 0.0F, false);
		bystander.teleportTo(nether, 2.5, 100, 0.5, Set.of(), 0.0F, 0.0F, false);
		h.assertTrue(p.level() == nether, "moved");
		h.assertFalse(ev.isAffected(p), "removed from the instance");
		h.assertFalse(p.hasEffect(MobEffects.SPEED), "effects reverted");
		h.assertTrue(ev.bossBarPlayers().isEmpty(), "boss bar removed");
		h.assertFalse(bystander.hasEffect(MobEffects.SPEED), "bystander reverted");
		h.assertTrue(manager(h).entries(p).isEmpty(), "client sync empty");
		cleanup(h, p, bystander);
		h.succeed();
	}

	/** A name tag used during the event wins over the stored original. */
	@GameTest
	public void nameTagDuringEventIsKept(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(3.5, 2, 3.5));
		Pig renamed = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 3.5));
		Pig untouched = mob(h, EntityTypes.PIG, new Vec3(3.5, 2, 4.5));
		ActiveEvent ev = TestSupport.start(h, "test_resources", p);
		h.assertTrue(renamed.hasCustomName() && untouched.hasCustomName(), "both renamed");
		renamed.setCustomName(Component.literal("Bacon")); // the player's name tag
		manager(h).stop(ev, StopReason.FORCED);
		h.assertValueEqual(renamed.getCustomName().getString(), "Bacon", "name tag kept");
		h.assertFalse(renamed.hasAttached(Marks.NAME), "mark removed");
		h.assertFalse(untouched.hasCustomName(), "the other one restored");
		renamed.discard();
		untouched.discard();
		cleanup(h, p);
		h.succeed();
	}

	/** Owned spawns bring no unowned side spawns: passengers are owned, zombies never call reinforcements. */
	@GameTest
	public void ownedSpawnsHaveNoSideSpawns(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = TestSupport.start(h, "test_marker", p);
		Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 3.5));
		Spider spider = EntityTypes.SPIDER.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
		Skeleton rider = EntityTypes.SKELETON.create(h.getLevel(), EntitySpawnReason.JOCKEY);
		h.assertTrue(spider != null && rider != null, "created");
		spider.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		rider.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		spider.setNoAi(true);
		rider.setNoAi(true);
		rider.startRiding(spider, true, false);
		h.assertTrue(ev.entities().spawn(spider) == spider, "spawned");
		h.assertTrue(!rider.isRemoved() && rider.level() == h.getLevel() && ev.entities().owns(rider), "passenger added and owned");
		h.assertTrue(OwnedEntities.isUnsaved(rider), "passenger never saved");
		Zombie zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
		zombie.snapTo(at.x + 1, at.y, at.z, 0.0F, 0.0F);
		zombie.setNoAi(true);
		h.assertTrue(ev.entities().spawnMob(zombie, EntitySpawnReason.EVENT) == zombie, "zombie spawned");
		h.assertTrue(zombie.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) == 0.0, "no reinforcements");
		h.assertFalse(zombie.isPassenger(), "no chicken jockey");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(spider.isRemoved() && rider.isRemoved() && zombie.isRemoved(), "all removed at the end");
		cleanup(h, p);
		h.succeed();
	}

	/** A replacement whose original cannot be restored stays as a normal entity instead of vanishing with it. */
	@GameTest
	public void failedRestoreKeepsTheReplacement(GameTestHelper h) {
		Chicken chicken = mob(h, EntityTypes.CHICKEN, new Vec3(2.5, 2, 2.5));
		CompoundTag broken = new CompoundTag();
		broken.putString("id", "chaosaholic:no_such_entity");
		chicken.addTag(Marks.OWNED_TAG);
		chicken.setAttached(Marks.OWNER, new Marks.OwnerMark(UUID.randomUUID(), Optional.of(broken)));
		OwnedEntities.revert(chicken);
		h.assertFalse(chicken.isRemoved(), "replacement kept");
		h.assertFalse(chicken.hasAttached(Marks.OWNER) || chicken.entityTags().contains(Marks.OWNED_TAG), "no longer owned");
		chicken.discard();
		h.succeed();
	}

	/** Fair game is mobs only: never an armor stand. */
	@GameTest
	public void armorStandIsNotFairGame(GameTestHelper h) {
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(h.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = h.absoluteVec(new Vec3(2.5, 2, 2.5));
		stand.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		h.getLevel().addFreshEntity(stand);
		Pig pig = mob(h, EntityTypes.PIG, new Vec3(3.5, 2, 2.5));
		h.assertFalse(Area.isFairGame(stand), "armor stand");
		h.assertTrue(Area.isFairGame(pig), "pig");
		h.assertFalse(Area.mobs(h.getLevel(), at, 2).contains(stand), "not in Area.mobs");
		stand.discard();
		pig.discard();
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
