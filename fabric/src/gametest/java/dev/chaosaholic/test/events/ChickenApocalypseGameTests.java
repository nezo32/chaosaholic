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
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Marks;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.impl.ChickenApocalypse;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * chicken_apocalypse: fair-game mobs near the player become chickens (named / far mobs untouched), the original
 * (same UUID, health, equipment) comes back at the end, on logout (NO_PLAYERS) and on load after the owner is gone;
 * a dead chicken means the original is gone; at most MAX_CHICKENS per instance.
 *
 * <p>Isolation: converting mobs removes the entity objects other tests hold, so this class runs in its own batch
 * (environment {@code chaosaholic-gametest:chicken_apocalypse}, an empty all_of definition) and is ONE sequential
 * test (parallel steps would convert each other's mobs).
 */
public class ChickenApocalypseGameTests {
	public static final String ISOLATED = "chaosaholic-gametest:chicken_apocalypse";

	private static Entity byUuid(GameTestHelper h, UUID id) {
		return h.getLevel().getEntity(id);
	}

	@GameTest(environment = ISOLATED, maxTicks = 300)
	public void convertsAndRestoresOnEveryPath(GameTestHelper h) {
		defaults(h);
		Entity[] reloaded = new Entity[1];
		UUID[] original = new UUID[1];
		ActiveEvent[] loggedOut = new ActiveEvent[1];
		h.startSequence()
				.thenExecute(() -> endRestoresEverything(h))
				.thenExecuteAfter(1, () -> deadChickenMeansTheOriginalIsGone(h))
				.thenExecuteAfter(1, () -> capped(h))
				.thenExecuteAfter(1, () -> candidateRules(h))
				// chunk unload: the tracker forgets the chicken; loaded again with the owner gone, the original comes back
				.thenExecuteAfter(1, () -> {
					ServerPlayer p = survivalPlayer(h);
					Pig pig = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
					original[0] = pig.getUUID();
					ActiveEvent ev = start(h, "chicken_apocalypse", p);
					Entity chicken = ev.entities().list().getFirst();
					ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(chicken, h.getLevel());
					h.assertFalse(ev.entities().owns(chicken), "forgotten on unload");
					manager(h).stop(ev, StopReason.FORCED);
					h.assertFalse(chicken.isRemoved(), "unloaded chicken not touched by the stop");
					OwnedEntities.onEntityLoad(chicken, h.getLevel()); // loaded again, owner gone: reverted next tick
					reloaded[0] = chicken;
					cleanup(h, p);
				})
				.thenExecuteAfter(2, () -> {
					h.assertTrue(reloaded[0].isRemoved(), "chicken reverted on load");
					Entity back = byUuid(h, original[0]);
					h.assertTrue(back instanceof Pig, "pig restored on load");
					back.discard();
				})
				// logout of the only player: NO_PLAYERS on the next tick restores
				.thenExecuteAfter(1, () -> {
					ServerPlayer p = survivalPlayer(h);
					Pig pig = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
					original[0] = pig.getUUID();
					loggedOut[0] = start(h, "chicken_apocalypse", p);
					h.assertTrue(pig.isRemoved(), "converted");
					leave(h, p);
				})
				.thenWaitUntil(() -> h.assertTrue(loggedOut[0].isStopped(), "no players: ended"))
				.thenExecute(() -> {
					Entity back = byUuid(h, original[0]);
					h.assertTrue(back instanceof Pig, "pig back after logout");
					back.discard();
				})
				.thenSucceed();
	}

	private static void endRestoresEverything(GameTestHelper h) {
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		Zombie near = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 1.5));
		near.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		near.setHealth(7.0F);
		Zombie named = mob(h, EntityTypes.ZOMBIE, new Vec3(1.5, 2, 4.5));
		named.setCustomName(Component.literal("Bob"));
		Pig far = mob(h, EntityTypes.PIG, new Vec3(1.5, 2, 1.5 + ChickenApocalypse.RADIUS + 3));
		UUID id = near.getUUID();
		ActiveEvent ev = start(h, "chicken_apocalypse", p);
		h.assertTrue(near.isRemoved(), "zombie replaced");
		h.assertValueEqual(ev.entities().count(), 1, "one chicken");
		Entity chicken = ev.entities().list().getFirst();
		h.assertTrue(chicken instanceof Chicken, "a chicken");
		h.assertTrue(chicken.position().distanceTo(near.position()) < 0.01, "where the zombie stood");
		h.assertFalse(OwnedEntities.isUnsaved(chicken), "saved with the original (unload / crash safe)");
		h.assertFalse(named.isRemoved(), "named mob untouched");
		h.assertFalse(far.isRemoved(), "far mob untouched");
		chicken.teleportTo(chicken.getX() + 1, chicken.getY(), chicken.getZ());
		Vec3 at = chicken.position();
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(chicken.isRemoved(), "chicken gone");
		Entity back = byUuid(h, id);
		h.assertTrue(back instanceof Zombie, "original back with the same UUID: " + back);
		Zombie z = (Zombie) back;
		h.assertValueEqual(z.getHealth(), 7.0F, "health");
		h.assertTrue(z.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "equipment");
		h.assertTrue(z.position().distanceTo(at) < 0.01, "at the chicken's position");
		h.assertFalse(z.hasAttached(Marks.OWNER) || z.entityTags().contains(Marks.OWNED_TAG), "no marks left");
		z.discard();
		named.discard();
		far.discard();
		cleanup(h, p);
	}

	private static void deadChickenMeansTheOriginalIsGone(GameTestHelper h) {
		ServerPlayer p = survivalPlayer(h);
		Pig pig = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 1.5));
		UUID id = pig.getUUID();
		ActiveEvent ev = start(h, "chicken_apocalypse", p);
		Entity chicken = ev.entities().list().getFirst();
		chicken.kill(h.getLevel());
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(byUuid(h, id) == null, "no pig");
		cleanup(h, p);
	}

	private static void capped(GameTestHelper h) {
		ServerPlayer p = survivalPlayer(h);
		List<Pig> pigs = new ArrayList<>();
		for (int i = 0; i < ChickenApocalypse.MAX_CHICKENS + 4; i++) {
			pigs.add(mob(h, EntityTypes.PIG, new Vec3(1.5 + (i % 5), 2, 3.5 + (i / 5))));
		}
		List<UUID> ids = pigs.stream().map(Entity::getUUID).toList();
		ActiveEvent ev = start(h, "chicken_apocalypse", p);
		h.assertValueEqual(ev.entities().count(), ChickenApocalypse.MAX_CHICKENS, "capped");
		manager(h).stop(ev, StopReason.FORCED);
		for (UUID id : ids) {
			Entity e = byUuid(h, id);
			h.assertTrue(e instanceof Pig, "every pig is a pig again");
			e.discard();
		}
		cleanup(h, p);
	}

	private static void candidateRules(GameTestHelper h) {
		ServerPlayer p = survivalPlayer(h);
		Pig pig = mob(h, EntityTypes.PIG, new Vec3(4.5, 2, 4.5));
		Chicken chicken = mob(h, EntityTypes.CHICKEN, new Vec3(5.5, 2, 4.5));
		Zombie named = mob(h, EntityTypes.ZOMBIE, new Vec3(6.5, 2, 4.5));
		named.setCustomName(Component.literal("Bob"));
		h.assertTrue(ChickenApocalypse.isCandidate(pig), "pig");
		h.assertFalse(ChickenApocalypse.isCandidate(chicken), "already a chicken");
		h.assertFalse(ChickenApocalypse.isCandidate(named), "named");
		h.assertFalse(ChickenApocalypse.isCandidate(p), "player");
		pig.setLeashedTo(p, true);
		h.assertFalse(ChickenApocalypse.isCandidate(pig), "leashed");
		pig.removeLeash();
		pig.discard();
		chicken.discard();
		named.discard();
		cleanup(h, p);
	}
}
