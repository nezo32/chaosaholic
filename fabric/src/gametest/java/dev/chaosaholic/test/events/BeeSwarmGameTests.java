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

import java.util.List;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.OwnedEntities;
import dev.chaosaholic.event.impl.BeeSwarm;
import dev.chaosaholic.event.impl.mob.NoLoot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * bee_swarm: after the warning a difficulty-sized swarm (Normal: 3) angry at the player; owned, unsaved, no xp;
 * a bee that has stung is removed; all bees go at the end and with the player on logout. Mock players are
 * invulnerable, so the bees never actually sting in these tests.
 */
public class BeeSwarmGameTests {
	@GameTest(maxTicks = 200)
	public void angrySwarmAfterWarningThenRemoved(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bee_swarm", p);
		h.assertValueEqual(ev.entities().count(), 0, "nothing during the warning");
		int expected = BeeSwarm.swarmSize(h.getLevel().getDifficulty());
		h.startSequence()
				.thenWaitUntil(() -> h.assertValueEqual(ev.entities().count(), expected, "swarm size"))
				.thenIdle(25) // one re-anger pass
				.thenExecute(() -> {
					h.assertTrue(expected <= BeeSwarm.MAX_BEES, "cap");
					List<Entity> bees = ev.entities().list();
					for (Entity e : bees) {
						h.assertTrue(e instanceof Bee, "bee: " + e);
						Bee bee = (Bee) e;
						h.assertTrue(bee.getPersistentAngerTarget() != null && p.getUUID().equals(bee.getPersistentAngerTarget().getUUID()), "angry at the player");
						h.assertTrue(OwnedEntities.isUnsaved(bee), "never saved");
						h.assertTrue(NoLoot.isApplied(bee), "no xp / pickup");
						h.assertTrue(bee.distanceTo(p) <= BeeSwarm.MAX_DISTANCE + 4, "near the player");
					}
					manager(h).stop(ev, StopReason.FORCED);
					for (Entity e : bees) h.assertTrue(e.isRemoved(), "removed at the end");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void stungBeeIsRemoved(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bee_swarm", p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() > 0, "swarm"))
				.thenExecute(() -> {
					Bee bee = (Bee) ev.entities().list().getFirst();
					// Bee#setHasStung is private: flip the saved flag (as vanilla does after a sting)
					TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, bee.registryAccess());
					bee.saveWithoutId(out);
					CompoundTag tag = out.buildResult();
					tag.putBoolean("HasStung", true);
					bee.load(TagValueInput.create(ProblemReporter.DISCARDING, bee.registryAccess(), tag));
					h.assertTrue(bee.hasStung(), "stung");
				})
				.thenWaitUntil(() -> h.assertTrue(ev.entities().list().stream().noneMatch(e -> ((Bee) e).hasStung()), "stung bee gone"))
				.thenExecute(() -> {
					h.assertFalse(ev.isStopped(), "the event goes on");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void logoutRemovesTheSwarm(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bee_swarm", p);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.entities().count() > 0, "swarm"))
				.thenExecute(() -> {
					List<Entity> bees = ev.entities().list();
					leave(h, p);
					for (Entity e : bees) h.assertTrue(e.isRemoved(), "removed with the player");
				})
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "ended"))
				.thenSucceed();
	}

	@GameTest(maxTicks = 120)
	public void stopDuringWarningReleasesNothing(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent ev = start(h, "bee_swarm", p);
		manager(h).stop(ev, StopReason.FORCED);
		h.startSequence()
				.thenIdle(90)
				.thenExecute(() -> {
					h.assertValueEqual(ev.entities().count(), 0, "no bees");
					cleanup(h, p);
				})
				.thenSucceed();
	}

	@GameTest
	public void refusesPeacefulAndCreative(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		Difficulty before = h.getLevel().getDifficulty();
		try {
			server(h).setDifficulty(Difficulty.PEACEFUL, true);
			h.assertTrue(manager(h).trigger(event("bee_swarm"), p).isEmpty(), "refused on Peaceful");
		} finally {
			server(h).setDifficulty(before, true);
		}
		ServerPlayer creative = player(h, GameType.CREATIVE);
		h.assertTrue(manager(h).trigger(event("bee_swarm"), creative).isEmpty(), "Creative never affected");
		h.assertValueEqual(BeeSwarm.swarmSize(Difficulty.HARD), BeeSwarm.MAX_BEES, "hard = cap");
		cleanup(h, p, creative);
		h.succeed();
	}
}
