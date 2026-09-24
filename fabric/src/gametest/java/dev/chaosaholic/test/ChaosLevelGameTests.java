package dev.chaosaholic.test;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.settings;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.PlayerLevels;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.mode.Scope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * The core rule: every new experience level of a survival/adventure player starts one random event, 1 s apart,
 * with the anti-farming mark, caps, Creative/Spectator exclusion, switches, weights and scope.
 */
public class ChaosLevelGameTests {
	/** 3 levels at once = 3 events, started one second apart by the manager's own tick. */
	@GameTest(maxTicks = 200)
	public void threeLevelsStartThreeEvents(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		EventManager m = manager(h);
		p.giveExperienceLevels(3);
		int[] firstStartTick = {-1};
		h.startSequence()
				.thenWaitUntil(() -> h.assertValueEqual(m.startedFromQueue(p), 1, "first event"))
				.thenExecute(() -> {
					h.assertValueEqual(m.pending(p), 2, "two still queued (1 s apart)");
					firstStartTick[0] = h.getLevel().getServer().getTickCount();
				})
				.thenWaitUntil(() -> h.assertValueEqual(m.startedFromQueue(p), 3, "three events"))
				.thenExecute(() -> {
					int elapsed = h.getLevel().getServer().getTickCount() - firstStartTick[0];
					h.assertTrue(elapsed >= 2 * ChaosLimits.START_INTERVAL_TICKS - 1, "spaced out, elapsed " + elapsed);
					h.assertValueEqual(m.pending(p), 0, "queue empty");
					// the safe set has two events: three rolls = at least one extension, never a duplicate instance
					Set<String> ids = new HashSet<>();
					for (ActiveEvent ev : m.activeFor(p)) h.assertTrue(ids.add(ev.id()), "duplicate instance " + ev.id());
					cleanup(h, p);
				})
				.thenSucceed();
	}

	/** Spending levels (enchanting, anvil) and earning them back never triggers; only a new maximum does. */
	@GameTest
	public void antiFarming(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		EventManager m = manager(h);
		p.giveExperienceLevels(5);
		h.assertValueEqual(m.observe(p, true), 5, "levels 1-5");
		h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 5, "mark");
		p.giveExperienceLevels(-3); // enchanting table
		h.assertValueEqual(m.observe(p, true), 0, "spent");
		p.giveExperienceLevels(3); // earned back
		h.assertValueEqual(m.observe(p, true), 0, "regained: no event");
		p.giveExperienceLevels(-2);
		m.observe(p, true);
		p.giveExperienceLevels(4); // 3 -> 7: only 6 and 7 are new
		h.assertValueEqual(m.observe(p, true), 2, "past the mark");
		h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 7, "mark moved");
		h.assertValueEqual(m.pending(p), ChaosLimits.QUEUE_CAP >= 7 ? 7 : ChaosLimits.QUEUE_CAP, "pending");
		cleanup(h, p);
		h.succeed();
	}

	/** First observation initializes the mark: a player who joins with levels (existing world) triggers nothing. */
	@GameTest
	public void existingLevelsDoNotFlood(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.removeAttached(PlayerLevels.MARK);
		p.giveExperienceLevels(40);
		h.assertValueEqual(manager(h).observe(p, true), 0, "first observation");
		h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 40, "mark = current level");
		p.giveExperienceLevels(1);
		h.assertValueEqual(manager(h).observe(p, true), 1, "next level counts");
		cleanup(h, p);
		h.succeed();
	}

	/** Death resets the mark: the respawned player (no attachment, not copied on death) counts from its new level. */
	@GameTest
	public void deathResetsTheMark(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.giveExperienceLevels(10);
		manager(h).observe(p, true);
		TestSupport.start(h, "speed_demon", p);
		p.die(h.getLevel().damageSources().fellOutOfWorld());
		h.assertTrue(manager(h).activeFor(p).isEmpty(), "events of the dead player ended");
		h.assertValueEqual(manager(h).pending(p), 0, "queue cleared on death");
		ServerPlayer respawned = h.getLevel().getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
		h.assertTrue(respawned.getAttached(PlayerLevels.MARK) == null, "mark not copied on death");
		h.assertValueEqual(respawned.experienceLevel, 0, "levels lost");
		h.assertValueEqual(manager(h).observe(respawned, true), 0, "first observation after respawn");
		respawned.setGameMode(GameType.SURVIVAL);
		respawned.giveExperienceLevels(2);
		h.assertValueEqual(manager(h).observe(respawned, true), 2, "levels 1-2 count again");
		cleanup(h, respawned);
		h.succeed();
	}

	@GameTest
	public void queueIsCapped(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.giveExperienceLevels(1000);
		h.assertValueEqual(manager(h).observe(p, true), ChaosLimits.QUEUE_CAP, "accepted");
		h.assertValueEqual(manager(h).pending(p), ChaosLimits.QUEUE_CAP, "pending");
		h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 1000, "mark still moves");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void creativeAndSpectatorNeverTrigger(GameTestHelper h) {
		defaults(h);
		for (GameType mode : new GameType[] {GameType.CREATIVE, GameType.SPECTATOR}) {
			ServerPlayer p = survivalPlayer(h);
			p.setGameMode(mode);
			h.assertFalse(EventManager.isEligible(p), mode + " eligible");
			p.giveExperienceLevels(5);
			h.assertValueEqual(manager(h).observe(p, true), 0, mode + " level-up");
			h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 5, mode + " mark still updated (no farming by switching)");
			h.assertTrue(manager(h).trigger(event("speed_demon"), p).isEmpty(), mode + " trigger refused");
			h.assertTrue(manager(h).roll(p).isEmpty(), mode + " roll refused");
			p.setGameMode(GameType.SURVIVAL);
			h.assertValueEqual(manager(h).observe(p, true), 0, mode + " back in survival: old levels do not count");
			cleanup(h, p);
		}
		ServerPlayer adventure = survivalPlayer(h);
		adventure.setGameMode(GameType.ADVENTURE);
		adventure.giveExperienceLevels(1);
		h.assertValueEqual(manager(h).observe(adventure, true), 1, "adventure counts");
		cleanup(h, adventure);
		h.succeed();
	}

	/** A player who switches to Creative mid-event is removed and reverted; world scope never includes them. */
	@GameTest
	public void creativeIsNeverAffected(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer creative = survivalPlayer(h);
		creative.setGameMode(GameType.CREATIVE);
		try {
			settings(h).setScope(Scope.WORLD);
			ActiveEvent ev = TestSupport.start(h, "test_marker", p);
			h.assertTrue(ev.isAffected(p), "trigger player in world scope");
			h.assertFalse(ev.isAffected(creative), "creative in world scope");
			h.assertFalse(creative.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "creative got the effect");
			manager(h).stop(ev, StopReason.FORCED);
		} finally {
			defaults(h);
		}
		ActiveEvent ev = TestSupport.start(h, "speed_demon", p);
		h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "speed given");
		p.setGameMode(GameType.CREATIVE);
		h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(ev.isStopped(), "event ended without players"))
				.thenExecute(() -> {
					h.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "speed reverted");
					h.assertTrue(ev.bossBarPlayers().isEmpty(), "boss bar removed");
					cleanup(h, p, creative);
				})
				.thenSucceed();
	}

	@GameTest
	public void modeOffNeverQueues(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		p.giveExperienceLevels(3);
		h.assertValueEqual(manager(h).observe(p, false), 0, "mode off");
		h.assertValueEqual(p.getAttached(PlayerLevels.MARK), 3, "mark follows even when off (no flood on /chaosaholic on)");
		p.giveExperienceLevels(1);
		h.assertValueEqual(manager(h).observe(p, true), 1, "only the new level after turning on");
		cleanup(h, p);
		h.succeed();
	}

	/** Disabled events and weight-0 events are never rolled. Synchronous (the switches are shared). */
	@GameTest
	public void switchesAndWeightsControlRolls(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		try {
			settings(h).setEventEnabled(event("speed_demon"), false);
			for (int i = 0; i < 20; i++) {
				Optional<ActiveEvent> ev = manager(h).roll(p);
				h.assertTrue(ev.isPresent(), "rolled");
				h.assertValueEqual(ev.get().id(), "feather_fall", "only enabled event");
				manager(h).stop(ev.get(), StopReason.FORCED);
			}
			settings(h).setEventEnabled(event("speed_demon"), true);
			settings(h).setWeight(event("feather_fall"), 0);
			for (int i = 0; i < 20; i++) {
				ActiveEvent ev = manager(h).roll(p).orElseThrow();
				h.assertValueEqual(ev.id(), "speed_demon", "weight 0 never rolled");
				manager(h).stop(ev, StopReason.FORCED);
			}
			settings(h).setWeight(event("speed_demon"), 0);
			h.assertTrue(manager(h).roll(p).isEmpty(), "all weights 0: nothing rolls");
		} finally {
			defaults(h);
			cleanup(h, p);
		}
		h.succeed();
	}

	/**
	 * Scope player: only the trigger player. Scope world: every eligible player of that dimension (test_marker: a
	 * world-scope start would extend other tests' instances of a shared event).
	 */
	@GameTest
	public void scopePlayerVersusWorld(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer a = survivalPlayer(h);
		ServerPlayer b = survivalPlayer(h);
		try {
			ActiveEvent own = TestSupport.start(h, "test_marker", a);
			h.assertTrue(own.isAffected(a) && !own.isAffected(b), "player scope");
			manager(h).stop(own, StopReason.FORCED);
			settings(h).setScope(Scope.WORLD);
			ActiveEvent shared = TestSupport.start(h, "test_marker", a);
			h.assertTrue(shared.isAffected(a) && shared.isAffected(b), "world scope includes b");
			h.assertTrue(shared.bossBarPlayers().contains(b), "b sees the bar");
			h.assertTrue(b.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "b got the effect");
			manager(h).stop(shared, StopReason.FORCED);
			h.assertFalse(b.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "b reverted");
		} finally {
			defaults(h);
			cleanup(h, a, b);
		}
		h.succeed();
	}

	/** Same event again on the same player extends it (capped); different events stack; at most 8 per player. */
	@GameTest
	public void stackingExtendsAndCaps(GameTestHelper h) {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer p = survivalPlayer(h);
		ActiveEvent first = TestSupport.start(h, "speed_demon", p);
		int before = first.remainingTicks();
		ActiveEvent again = TestSupport.start(h, "speed_demon", p);
		h.assertTrue(again == first, "same instance");
		h.assertTrue(first.remainingTicks() > before, "extended");
		for (int i = 0; i < 10; i++) TestSupport.start(h, "speed_demon", p);
		h.assertValueEqual(first.remainingTicks(), ChaosLimits.MAX_REMAINING_TICKS, "capped at 180 s");
		h.assertTrue(first.totalTicks() >= first.remainingTicks(), "progress <= 1");
		TestSupport.start(h, "feather_fall", p);
		h.assertValueEqual(manager(h).activeFor(p).size(), 2, "different events stack");
		h.assertValueEqual(manager(h).activeCount(p), 2, "count");
		h.assertTrue(ChaosLimits.MAX_ACTIVE_PER_PLAYER == 8, "cap");
		cleanup(h, p);
		h.succeed();
	}
}
