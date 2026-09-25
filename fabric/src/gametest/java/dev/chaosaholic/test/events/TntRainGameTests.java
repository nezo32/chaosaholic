package dev.chaosaholic.test.events;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.leave;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.player;
import static dev.chaosaholic.test.TestSupport.server;
import static dev.chaosaholic.test.TestSupport.start;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.ArrayList;
import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Warning;
import dev.chaosaholic.event.impl.TntRain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * tnt_rain: nothing during the warning, then owned lit TNT 4-10 blocks around the player (capped), removed when the
 * event ends (stop, logout); the event's own detonation replaces vanilla's and breaks blocks only with mobGriefing.
 * TNT is never allowed to explode near the parallel test structures: tests stop the event well before the fuse ends,
 * and detonation tests run high above the structure (inside its force-loaded chunks: the rain's own TNT mostly lands
 * outside, where entities may not tick, so only its spawning and removal are checked).
 */
public class TntRainGameTests {
	@GameTest(maxTicks = 200)
	public void warningThenTntAroundPlayerThenStopRemovesIt(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "tnt_rain", p);
		int delay = Warning.delay(ev.context());
		h.assertValueEqual(ev.entities().count(), 0, "no TNT at start");
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() >= 1, "TNT dropped"))
				.thenExecute(() -> {
					h.assertTrue(ev.age() >= delay, "no TNT before the warning ended: age " + ev.age());
					List<Entity> tnt = ev.entities().list();
					for (Entity e : tnt) {
						h.assertTrue(e instanceof PrimedTnt, "owned entity is TNT: " + e);
						h.assertTrue(e.entityTags().contains(TntRain.TNT_TAG), "tagged");
						double dist = Math.sqrt(sq(e.getX() - p.getX()) + sq(e.getZ() - p.getZ()));
						h.assertTrue(dist >= TntRain.MIN_DISTANCE && dist <= TntRain.MAX_DISTANCE + 1, "never onto the player: " + dist);
						h.assertTrue(((PrimedTnt) e).getFuse() > 40, "long fuse: " + ((PrimedTnt) e).getFuse());
					}
					manager(h).stop(ev, StopReason.FORCED);
					for (Entity e : tnt) h.assertTrue(e.isRemoved(), "TNT removed at the end");
					h.assertValueEqual(ev.entities().count(), 0, "nothing owned left");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void cappedAndRemovedOnLogout(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "tnt_rain", p);
		for (int i = 0; i < 40; i++) TntRain.plan(ev, p);
		h.assertTrue(TntRain.planned(ev) <= TntRain.MAX_TNT, "planned capped: " + TntRain.planned(ev));
		List<Entity> spawned = new ArrayList<>();
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() >= 1, "planned TNT spawned"))
				.thenExecute(() -> {
					h.assertTrue(ev.entities().count() <= TntRain.MAX_TNT, "spawned capped: " + ev.entities().count());
					spawned.addAll(ev.entities().list());
					leave(h, p);
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "no players left: ended"))
				.thenExecute(() -> {
					for (Entity e : spawned) h.assertTrue(e.isRemoved(), "TNT removed after logout");
				})
				.thenSucceed();
	}

	/** onTick replaces the vanilla explosion one tick early (fuse 1): counted as the event's own detonation. */
	@GameTest(maxTicks = 40)
	public void eventDetonatesInsteadOfVanilla(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "tnt_rain", p);
		PrimedTnt tnt = TntRain.spawn(ev, h.absoluteVec(new Vec3(4.5, 40, 4.5)));
		h.assertTrue(tnt != null, "spawned in the sky");
		tnt.setNoGravity(true);
		tnt.setFuse(4);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(tnt.isRemoved(), "detonated"))
				.thenExecute(() -> {
					h.assertValueEqual(TntRain.detonations(ev), 1, "own detonation");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** Synchronous (shared game rules are restored before returning), far above the test structure. */
	@GameTest
	public void detonationBreaksBlocksOnlyWithMobGriefing(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		GameRules rules = level.getGameRules();
		boolean griefing = rules.get(GameRules.MOB_GRIEFING);
		boolean tntExplodes = rules.get(GameRules.TNT_EXPLODES);
		BlockPos glass = h.absolutePos(new BlockPos(4, 40, 4));
		try {
			level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
			rules.set(GameRules.MOB_GRIEFING, false, server(h));
			TntRain.detonate(level, tntNextTo(level, glass));
			h.assertTrue(level.getBlockState(glass).is(Blocks.GLASS), "mobGriefing off: no block damage");
			rules.set(GameRules.MOB_GRIEFING, true, server(h));
			rules.set(GameRules.TNT_EXPLODES, false, server(h));
			TntRain.detonate(level, tntNextTo(level, glass));
			h.assertTrue(level.getBlockState(glass).is(Blocks.GLASS), "tntExplodes off: no explosion");
			rules.set(GameRules.TNT_EXPLODES, true, server(h));
			PrimedTnt tnt = tntNextTo(level, glass);
			TntRain.detonate(level, tnt);
			h.assertTrue(level.getBlockState(glass).isAir(), "mobGriefing on: block destroyed");
			h.assertTrue(tnt.isRemoved(), "TNT discarded");
		} finally {
			rules.set(GameRules.MOB_GRIEFING, griefing, server(h));
			rules.set(GameRules.TNT_EXPLODES, tntExplodes, server(h));
			level.setBlockAndUpdate(glass, Blocks.AIR.defaultBlockState());
		}
		h.succeed();
	}

	@GameTest
	public void creativeRefusedAndLethalCheck(GameTestHelper h) {
		defaults(h);
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("tnt_rain"), creative).isEmpty(), "Creative is never affected");
		cleanup(h, creative);
		ServerPlayer p = survivalPlayer(h);
		h.assertTrue(TntRain.isLethal(p, p.getHealth()), "full-health hit is lethal");
		h.assertFalse(TntRain.isLethal(p, p.getHealth() - 1), "lower hit is not");
		cleanup(h, p);
		h.succeed();
	}

	private static PrimedTnt tntNextTo(ServerLevel level, BlockPos pos) {
		return new PrimedTnt(level, pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5, null);
	}

	private static double sq(double d) {
		return d * d;
	}

	/** A player the event does not affect (a bystander) never gets TNT dropped next to them either. */
	@GameTest
	public void neverOnABystander(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h, new Vec3(1.5, 2, 1.5));
		ServerPlayer bystander = survivalPlayer(h, new Vec3(7.5, 2, 1.5));
		ActiveEvent ev = start(h, "tnt_rain", p);
		h.assertFalse(ev.isAffected(bystander), "bystander not affected");
		h.assertTrue(TntRain.spawn(ev, bystander.position()) == null, "no TNT on the bystander's feet");
		h.assertTrue(TntRain.spawn(ev, bystander.position().add(2, 0, 0)) == null, "nor next to them");
		h.assertValueEqual(ev.entities().count(), 0, "nothing spawned");
		manager(h).stop(ev, StopReason.FORCED);
		cleanup(h, p, bystander);
		h.succeed();
	}

	/**
	 * Hardcore paths (the test server is not Hardcore, so they are checked directly): a lethal hit by this
	 * instance's TNT is cancelled, other hits are not, and on Hardcore the explosion pushes no player.
	 */
	@GameTest
	public void hardcoreCancelsLethalHitsAndKnockback(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "tnt_rain", p);
		PrimedTnt tnt = TntRain.spawn(ev, h.absoluteVec(new Vec3(4.5, 40, 4.5)));
		h.assertTrue(tnt != null, "spawned in the sky");
		DamageSource ours = h.getLevel().damageSources().explosion(tnt, null);
		h.assertTrue(TntRain.isLethalHit(ev, p, ours, p.getHealth()), "lethal hit of our TNT");
		h.assertFalse(TntRain.isLethalHit(ev, p, ours, p.getHealth() - 1.0F), "survivable hit goes through");
		PrimedTnt other = new PrimedTnt(h.getLevel(), tnt.getX(), tnt.getY(), tnt.getZ(), null);
		h.assertFalse(TntRain.isLethalHit(ev, p, h.getLevel().damageSources().explosion(other, null), p.getHealth()), "someone else's TNT");
		h.assertTrue(event("tnt_rain").allowDamage(ev, p, ours, p.getHealth()) != ev.context().isHardcore(), "cancelled only on Hardcore");
		Pig pig = EntityTypes.PIG.create(h.getLevel(), EntitySpawnReason.EVENT);
		ExplosionDamageCalculator hardcore = TntRain.damageCalculator(tnt, true);
		h.assertValueEqual(hardcore.getKnockbackMultiplier(p), 0.0F, "Hardcore: no knockback for players");
		h.assertValueEqual(hardcore.getKnockbackMultiplier(pig), 1.0F, "mobs are still pushed");
		h.assertValueEqual(TntRain.damageCalculator(tnt, false).getKnockbackMultiplier(p), 1.0F, "vanilla knockback otherwise");
		manager(h).stop(ev, StopReason.FORCED);
		h.assertTrue(tnt.isRemoved(), "TNT removed");
		cleanup(h, p);
		h.succeed();
	}
}
