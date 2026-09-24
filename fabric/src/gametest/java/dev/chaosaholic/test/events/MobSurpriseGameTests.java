package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.server;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.impl.MobSurprise;
import dev.chaosaholic.event.impl.mob.NoLoot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * mob_surprise: nothing during the warning, then a difficulty-sized wave 8-12 blocks away, owned, unsaved and
 * loot-free; discarded at the end, with the player on logout, and never spawned when stopped during the warning.
 * The gametest server runs on Normal: waves of 3.
 */
public class MobSurpriseGameTests {
	@GameTest(maxTicks = 200)
	public void waveAfterWarningThenDiscarded(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "mob_surprise", p);
		h.assertValueEqual(ev.entities().count(), 0, "nothing during the warning");
		int expected = MobSurprise.waveSize(h.getLevel().getDifficulty());
		h.startSequence()
				.thenIdle(30)
				.thenExecute(() -> h.assertValueEqual(ev.entities().count(), 0, "still warning after 1.5 s"))
				.thenWaitUntil(() -> h.assertValueEqual(ev.entities().count(), expected, "wave size"))
				.thenExecute(() -> {
					List<Entity> wave = ev.entities().list();
					for (Entity e : wave) {
						h.assertTrue(e instanceof Enemy, "hostile: " + e);
						h.assertTrue(e.entityTags().contains(Marks.OWNED_TAG), "owned tag");
						h.assertTrue(OwnedEntities.isUnsaved(e), "never saved");
						h.assertTrue(e instanceof Mob m && NoLoot.isApplied(m), "no loot / xp / pickup");
						h.assertFalse(e.isPassenger() || e.isVehicle(), "no jockeys");
						double dx = e.getX() - p.getX();
						double dz = e.getZ() - p.getZ();
						double d = Math.sqrt(dx * dx + dz * dz);
						h.assertTrue(d >= MobSurprise.MIN_DISTANCE - 1 && d <= MobSurprise.MAX_DISTANCE + 1, "distance " + d);
						h.assertTrue(h.getLevel().noCollision(e, e.getBoundingBox()), "not inside blocks");
					}
					manager(h).stop(ev, StopReason.FORCED);
					for (Entity e : wave) h.assertTrue(e.isRemoved(), "discarded at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 120)
	public void stopDuringWarningSpawnsNothing(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "mob_surprise", p);
		manager(h).stop(ev, StopReason.FORCED);
		h.startSequence()
				.thenIdle(90)
				.thenExecute(() -> {
					h.assertValueEqual(ev.entities().count(), 0, "no wave");
					AABB box = new AABB(p.position(), p.position()).inflate(MobSurprise.MAX_DISTANCE + 2);
					List<Entity> ours = h.getLevel().getEntities((Entity) null, box,
							e -> e.getAttached(Marks.OWNER) != null && e.getAttached(Marks.OWNER).owner().equals(ev.uuid()));
					h.assertTrue(ours.isEmpty(), "nothing of this instance in the world: " + ours);
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void logoutTakesTheWaveAlong(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "mob_surprise", p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() > 0, "wave"))
				.thenExecute(() -> {
					List<Entity> wave = ev.entities().list();
					leave(h, p);
					for (Entity e : wave) h.assertTrue(e.isRemoved(), "removed with the player");
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenSucceed();
	}

	/** Peaceful refuses (switched synchronously and back: difficulty is server-global); Creative is never a target. */
	@GameTest
	public void refusesPeacefulAndCreative(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Difficulty before = h.getLevel().getDifficulty();
		try {
			server(h).setDifficulty(Difficulty.PEACEFUL, true);
			h.assertTrue(manager(h).trigger(event("mob_surprise"), p).isEmpty(), "refused on Peaceful");
		} finally {
			server(h).setDifficulty(before, true);
		}
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("mob_surprise"), creative).isEmpty(), "Creative never affected");
		h.assertValueEqual(MobSurprise.waveSize(Difficulty.EASY), 2, "easy");
		h.assertValueEqual(MobSurprise.waveSize(Difficulty.NORMAL), 3, "normal");
		h.assertValueEqual(MobSurprise.waveSize(Difficulty.HARD), 4, "hard");
		cleanup(h, p, creative);
		h.succeed();
	}

	/** A NoLoot mob killed by a player drops no items, no equipment and no experience (a normal one does). */
	@GameTest(maxTicks = 40)
	public void waveMobsDropNothing(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Zombie plain = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 5.5));
		Zombie event = mob(h, EntityTypes.ZOMBIE, new Vec3(5.5, 2, 5.5));
		for (Zombie z : List.of(plain, event)) {
			z.setBaby(false); // babies never drop loot or xp: that would make the control pointless
			z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			z.setDropChance(EquipmentSlot.HEAD, 1.0F);
		}
		NoLoot.apply(event);
		h.assertTrue(NoLoot.isApplied(event), "applied");
		h.assertFalse(NoLoot.isApplied(plain), "control");
		for (Zombie z : List.of(plain, event)) z.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000.0F);
		AABB plainBox = plain.getBoundingBox().inflate(1.5);
		AABB eventBox = event.getBoundingBox().inflate(1.5);
		h.assertFalse(h.getLevel().getEntitiesOfClass(ItemEntity.class, plainBox).isEmpty(), "control drops its helmet");
		h.assertFalse(h.getLevel().getEntitiesOfClass(ExperienceOrb.class, plainBox).isEmpty(), "control drops xp");
		h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, eventBox).isEmpty(), "no items");
		h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class, eventBox).isEmpty(), "no xp");
		for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, plainBox)) item.discard();
		for (ExperienceOrb orb : h.getLevel().getEntitiesOfClass(ExperienceOrb.class, plainBox)) orb.discard();
		plain.discard();
		event.discard();
		cleanup(h, p);
		h.succeed();
	}

	/**
	 * A wave mob that converts (here: into a drowned, as a zombie does under water) stays worthless to farm, owned,
	 * breaks no doors and keeps hunting the same player.
	 */
	@GameTest(maxTicks = 200)
	public void convertedWaveMobKeepsNoLootAndTarget(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "mob_surprise", p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() > 0, "wave"))
				.thenExecute(() -> {
					Mob mob = (Mob) ev.entities().list().getFirst();
					h.assertTrue(MobSurprise.huntedBy(ev, mob) == p, "wave mob hunts the player");
					Drowned d = mob.convertTo(EntityTypes.DROWNED, ConversionParams.single(mob, true, true), x -> {});
					h.assertTrue(d != null && mob.isRemoved(), "converted");
					h.assertTrue(ev.entities().owns(d), "the drowned is owned");
					h.assertTrue(OwnedEntities.isUnsaved(d), "never saved");
					h.assertTrue(NoLoot.isApplied(d), "no loot / xp / pickup after the conversion");
					h.assertFalse(d.canBreakDoors(), "breaks no doors");
					h.assertTrue(MobSurprise.huntedBy(ev, d) == p, "still hunts the player");
					h.assertTrue(d.getTarget() == p, "targets the player at once");
					manager(h).stop(ev, StopReason.FORCED);
					h.assertTrue(d.isRemoved(), "discarded at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
