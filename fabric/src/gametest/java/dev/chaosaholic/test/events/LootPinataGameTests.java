package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.mob;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.impl.LootPinata;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * loot_pinata: the routed AFTER_DEATH hook drops 1-3 prize stacks per kill of an affected player, capped per run. The
 * tests fire the Fabric event directly (the victim stays alive, so only bonus items are on the ground).
 */
public class LootPinataGameTests {
	/** Fires AFTER_DEATH for {@code victim} killed by {@code killer}; returns (and removes) the items that dropped. */
	private static List<ItemEntity> kill(GameTestHelper h, ServerPlayer killer, LivingEntity victim) {
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(victim, h.getLevel().damageSources().playerAttack(killer));
		List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(victim.blockPosition()).inflate(2.0));
		items.forEach(ItemEntity::discard);
		return items;
	}

	private static boolean fromPool(ItemEntity e) {
		return LootPinata.POOL.stream().anyMatch(p -> e.getItem().is(p.item())
				&& e.getItem().getCount() >= p.min() && e.getItem().getCount() <= p.max());
	}

	@GameTest
	public void killsDropPrizesForAffectedPlayersOnly(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer other = survivalPlayer(h);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 4.5));
		h.assertTrue(kill(h, p, zombie).isEmpty(), "nothing before the event");
		ActiveEvent ev = start(h, LootPinata.ID, p);
		h.assertTrue(ev.hasBossBar(), "timed");
		int total = 0;
		for (int i = 0; i < 5; i++) {
			List<ItemEntity> drops = kill(h, p, zombie);
			h.assertTrue(drops.size() >= 1 && drops.size() <= LootPinata.MAX_STACKS_PER_KILL, "1-3 stacks per kill: " + drops.size());
			h.assertTrue(drops.stream().allMatch(LootPinataGameTests::fromPool), "prizes from the pool");
			total += drops.size();
		}
		h.assertValueEqual(LootPinata.dropped(ev), total, "counted");
		h.assertTrue(kill(h, other, zombie).isEmpty(), "killer outside the event: nothing");
		ArmorStand stand = h.spawn(EntityTypes.ARMOR_STAND, new Vec3(2.5, 2, 4.5));
		h.assertTrue(kill(h, p, stand).isEmpty(), "armor stands are not mobs");
		stand.discard();
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(kill(h, p, zombie).isEmpty(), "nothing after the end");
		zombie.discard();
		cleanup(h, p, other);
		h.succeed();
	}

	@GameTest
	public void cappedPerRun(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 4.5));
		ActiveEvent ev = start(h, LootPinata.ID, p);
		int total = 0;
		for (int i = 0; i < LootPinata.MAX_STACKS_PER_EVENT + 10; i++) total += kill(h, p, zombie).size();
		h.assertValueEqual(total, LootPinata.MAX_STACKS_PER_EVENT, "stacks per run");
		h.assertValueEqual(LootPinata.dropped(ev), LootPinata.MAX_STACKS_PER_EVENT, "counter");
		ActiveEvent again = start(h, LootPinata.ID, p);
		h.assertTrue(again == ev, "extension keeps the run");
		h.assertTrue(kill(h, p, zombie).isEmpty(), "an extension does not reset the cap");
		zombie.discard();
		cleanup(h, p);
		h.succeed();
	}

	/** Changes a shared game rule: synchronous only, restored before returning. */
	@GameTest
	public void respectsMobDropsRule(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 4.5));
		start(h, LootPinata.ID, p);
		GameRules rules = h.getLevel().getGameRules();
		boolean before = rules.get(GameRules.MOB_DROPS);
		try {
			rules.set(GameRules.MOB_DROPS, false, h.getLevel().getServer());
			h.assertTrue(kill(h, p, zombie).isEmpty(), "mob_drops off: nothing");
		} finally {
			rules.set(GameRules.MOB_DROPS, before, h.getLevel().getServer());
		}
		zombie.discard();
		cleanup(h, p);
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void creativeAndLogoutEndIt(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer q = survivalPlayer(h);
		Zombie zombie = mob(h, EntityTypes.ZOMBIE, new Vec3(4.5, 2, 4.5));
		ActiveEvent ev = start(h, LootPinata.ID, p);
		ActiveEvent ev2 = start(h, LootPinata.ID, q);
		p.setGameMode(GameType.CREATIVE);
		h.assertTrue(kill(h, p, zombie).isEmpty(), "creative killer: nothing");
		leave(h, q);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped() && ev2.isStopped(), "both ended"))
				.thenExecute(() -> {
					zombie.discard();
					cleanup(h, p);
				})
				.thenSucceed();
	}
}
