package dev.chaosaholic.test;

import static dev.chaosaholic.test.TestSupport.cleanup;
import static dev.chaosaholic.test.TestSupport.defaults;
import static dev.chaosaholic.test.TestSupport.event;
import static dev.chaosaholic.test.TestSupport.manager;
import static dev.chaosaholic.test.TestSupport.settings;
import static dev.chaosaholic.test.TestSupport.survivalPlayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.chaosaholic.Texts;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.EventIds;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import dev.chaosaholic.event.EventRegistry;
import dev.chaosaholic.event.RemoveReason;
import dev.chaosaholic.mixin.MinecraftServerAccessor;
import dev.chaosaholic.mode.ChaosSettings;
import dev.chaosaholic.mode.ModeBootstrap;
import dev.chaosaholic.mode.PendingWorldMode;
import dev.chaosaholic.mode.Scope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * /chaosaholic commands, per-world settings (SavedData), the Create World handoff and the lang keys of every
 * registered event. Settings are server-global: every test here is synchronous and ends with {@code defaults}.
 * Tests that switch the mode OFF (which stops every running instance) run in {@link TestSupport#GLOBAL_STATE}.
 */
public class ChaosModeGameTests {
	private static int run(GameTestHelper h, CommandSourceStack source, String command) throws CommandSyntaxException {
		CommandDispatcher<CommandSourceStack> d = h.getLevel().getServer().getCommands().getDispatcher();
		return d.execute(command, source);
	}

	private static CommandSourceStack op(GameTestHelper h) {
		return h.getLevel().getServer().createCommandSourceStack();
	}

	@GameTest(environment = TestSupport.GLOBAL_STATE)
	public void commandOnOffStatus(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		try {
			h.assertValueEqual(run(h, op(h), "chaosaholic off"), 0, "off result");
			h.assertFalse(settings(h).enabled(), "off stored");
			h.assertValueEqual(run(h, op(h), "chaosaholic status"), 0, "status off");
			h.assertValueEqual(run(h, op(h), "chaosaholic"), 0, "bare = status");
			h.assertValueEqual(run(h, op(h), "chaosaholic on"), 1, "on result");
			h.assertTrue(settings(h).enabled(), "on stored");
			h.assertValueEqual(run(h, op(h), "chaosaholic status"), 1, "status on");
		} finally {
			defaults(h);
		}
		h.succeed();
	}

	@GameTest
	public void commandNeedsGamemaster(GameTestHelper h) {
		defaults(h);
		MinecraftServer server = h.getLevel().getServer();
		ServerPlayer p = survivalPlayer(h);
		CommandSourceStack nobody = server.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
		for (CommandSourceStack source : new CommandSourceStack[] {nobody, p.createCommandSourceStack()}) {
			for (String cmd : new String[] {"chaosaholic off", "chaosaholic", "chaosaholic scope world", "chaosaholic events",
					"chaosaholic trigger speed_demon", "chaosaholic event speed_demon weight 5"}) {
				try {
					run(h, source, cmd);
					h.fail("non-op could run /" + cmd);
				} catch (CommandSyntaxException expected) {
					// requires() hides the command
				}
			}
		}
		h.assertTrue(settings(h).enabled(), "mode unchanged");
		cleanup(h, p);
		h.succeed();
	}

	@GameTest
	public void commandScope(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		try {
			h.assertValueEqual(run(h, op(h), "chaosaholic scope"), 0, "scope status = player (0)");
			h.assertValueEqual(run(h, op(h), "chaosaholic scope world"), 1, "scope world");
			h.assertValueEqual(settings(h).scope(), Scope.WORLD, "stored scope");
			h.assertValueEqual(run(h, op(h), "chaosaholic scope"), 1, "scope status = world");
			h.assertValueEqual(run(h, op(h), "chaosaholic scope player"), 0, "scope player");
			h.assertValueEqual(settings(h).scope(), Scope.PLAYER, "stored scope");
		} finally {
			defaults(h);
		}
		h.succeed();
	}

	@GameTest
	public void commandEventSwitchAndWeight(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		ChaosEvent speed = event("speed_demon");
		try {
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon"), 1, "status on");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon off"), 0, "off");
			h.assertFalse(settings(h).isEventEnabled(speed), "stored off");
			h.assertValueEqual(settings(h).effectiveWeight(speed), 0, "disabled = weight 0 for rolls");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon status"), 0, "status off");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon on"), 1, "on");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon weight"), 100, "default weight");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon weight 250"), 250, "set weight");
			h.assertValueEqual(settings(h).weight(speed), 250, "stored weight");
			h.assertValueEqual(run(h, op(h), "chaosaholic event speed_demon weight 0"), 0, "weight 0");
			h.assertValueEqual(settings(h).effectiveWeight(speed), 0, "weight 0 stored");
			try {
				run(h, op(h), "chaosaholic event speed_demon weight 1001");
				h.fail("weight above 1000 accepted");
			} catch (CommandSyntaxException expected) {
				// out of range
			}
			h.assertValueEqual(run(h, op(h), "chaosaholic event no_such_event"), 0, "unknown event");
			h.assertValueEqual(run(h, op(h), "chaosaholic event no_such_event off"), 0, "unknown event off");
			h.assertTrue(run(h, op(h), "chaosaholic events") >= 2, "events list returns the enabled count");
		} finally {
			defaults(h);
		}
		h.succeed();
	}

	@GameTest
	public void commandTriggerAndStop(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer creative = survivalPlayer(h);
		creative.setGameMode(GameType.CREATIVE);
		try {
			settings(h).setEnabled(false); // trigger works with the mode off and the event switched off
			settings(h).setEventEnabled(event("speed_demon"), false);
			h.assertValueEqual(run(h, op(h), "chaosaholic trigger speed_demon " + p.getGameProfile().name()), 1, "trigger result");
			h.assertTrue(manager(h).find(event("speed_demon"), p) != null, "speed_demon runs");
			h.assertValueEqual(run(h, op(h), "chaosaholic trigger speed_demon " + creative.getGameProfile().name()), 0, "creative target refused");
			h.assertTrue(manager(h).activeFor(creative).isEmpty(), "creative untouched");
			h.assertValueEqual(run(h, op(h), "chaosaholic trigger no_such_event " + p.getGameProfile().name()), 0, "unknown id");
			h.assertValueEqual(run(h, op(h), "chaosaholic trigger test_refuse " + p.getGameProfile().name()), 0, "canStart false refused");
			h.assertValueEqual(run(h, op(h), "chaosaholic roll " + creative.getGameProfile().name()), 0, "creative roll refused");
			h.assertValueEqual(run(h, op(h), "chaosaholic stop " + p.getGameProfile().name()), 1, "stop result");
			h.assertTrue(manager(h).activeFor(p).isEmpty(), "stopped");
			defaults(h); // roll uses the switches: back to the safe set
			h.assertValueEqual(run(h, op(h), "chaosaholic roll " + p.getGameProfile().name()), 1, "roll result");
			h.assertTrue(TestSupport.SAFE_EVENTS.contains(manager(h).activeFor(p).getFirst().id()), "rolled a safe event");
		} finally {
			defaults(h);
			cleanup(h, p, creative);
		}
		h.succeed();
	}

	/**
	 * /chaosaholic stop on a world-scope instance shared with other players only lets the targets go (effects and boss
	 * bar reverted for them, the instance goes on for the rest); it ends the instance once the last player is stopped.
	 * A player-scope instance ends as before and leaves the other players' instances alone. GLOBAL_STATE: a world-scope
	 * start takes in every eligible player of the level.
	 */
	@GameTest(environment = TestSupport.GLOBAL_STATE)
	public void commandStopReleasesTargetsFromSharedEvents(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		TestEvents.ensureRegistered();
		ServerPlayer a = survivalPlayer(h);
		ServerPlayer b = survivalPlayer(h);
		try {
			settings(h).setScope(Scope.WORLD);
			ActiveEvent shared = TestSupport.start(h, "test_marker", a);
			h.assertTrue(shared.isAffected(a) && shared.isAffected(b), "world scope: both affected");
			h.assertValueEqual(run(h, op(h), "chaosaholic stop " + a.getGameProfile().name()), 1, "stop result");
			h.assertFalse(shared.isStopped(), "goes on for the others");
			h.assertFalse(shared.isAffected(a), "a released");
			h.assertFalse(a.hasEffect(MobEffects.GLOWING), "a reverted");
			h.assertFalse(shared.bossBarPlayers().contains(a), "bar gone for a");
			h.assertTrue(manager(h).activeFor(a).isEmpty(), "nothing left on a");
			h.assertTrue(shared.isAffected(b), "b still affected");
			h.assertTrue(b.hasEffect(MobEffects.GLOWING), "b keeps the effect");
			h.assertTrue(shared.bossBarPlayers().contains(b), "b keeps the bar");
			for (ServerPlayer other : shared.players()) { // other tests' players, if any: leave b as the last one
				if (other != b) manager(h).removePlayer(shared, other, RemoveReason.STOPPED);
			}
			h.assertValueEqual(run(h, op(h), "chaosaholic stop " + b.getGameProfile().name()), 1, "stop last player");
			h.assertTrue(shared.isStopped(), "last player stopped: the instance ends");
			h.assertFalse(b.hasEffect(MobEffects.GLOWING), "b reverted");
			h.assertTrue(shared.bossBarPlayers().isEmpty(), "bar removed");

			settings(h).setScope(Scope.PLAYER);
			ActiveEvent own = TestSupport.start(h, "test_marker", a);
			ActiveEvent other = TestSupport.start(h, "test_marker", b);
			h.assertValueEqual(run(h, op(h), "chaosaholic stop " + a.getGameProfile().name()), 1, "player scope stop");
			h.assertTrue(own.isStopped(), "player scope: stopped");
			h.assertFalse(a.hasEffect(MobEffects.GLOWING), "a reverted (player scope)");
			h.assertFalse(other.isStopped(), "b's own instance untouched");
			h.assertTrue(b.hasEffect(MobEffects.GLOWING), "b keeps its own effect");
		} finally {
			defaults(h);
			cleanup(h, a, b);
		}
		h.succeed();
	}

	/** Why trigger / roll started nothing: the reason behind each command error message. Synchronous. */
	@GameTest
	public void refusalReasons(GameTestHelper h) {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		ServerPlayer creative = survivalPlayer(h);
		creative.setGameMode(GameType.CREATIVE);
		EventManager m = manager(h);
		try {
			h.assertValueEqual(m.refusal(creative, null), EventManager.Refusal.INELIGIBLE, "creative roll");
			h.assertValueEqual(m.refusal(creative, event("speed_demon")), EventManager.Refusal.INELIGIBLE, "creative trigger");
			h.assertTrue(m.trigger(event("test_refuse"), p).isEmpty(), "refused");
			h.assertValueEqual(m.refusal(p, event("test_refuse")), EventManager.Refusal.CANNOT_START, "canStart false");
			for (ChaosEvent e : EventRegistry.all()) settings(h).setEventEnabled(e, false);
			h.assertTrue(m.roll(p).isEmpty(), "nothing enabled");
			h.assertValueEqual(m.refusal(p, null), EventManager.Refusal.ALL_OFF, "all off");
			defaults(h);
			for (int i = 1; i <= TestEvents.SLOTS; i++) TestSupport.start(h, TestEvents.slot(i), p);
			h.assertValueEqual(m.activeCount(p), ChaosLimits.MAX_ACTIVE_PER_PLAYER, "full");
			h.assertTrue(m.trigger(event("speed_demon"), p).isEmpty(), "no room for a ninth");
			h.assertValueEqual(m.refusal(p, event("speed_demon")), EventManager.Refusal.NO_ROOM, "no room");
			h.assertValueEqual(m.refusal(p, null), EventManager.Refusal.NO_ROOM, "no room (roll)");
			h.assertTrue(m.trigger(event(TestEvents.slot(1)), p).isPresent(), "a running event still extends");
			h.assertTrue(m.trigger(event("test_instant"), p).isPresent(), "instant events need no slot");
		} finally {
			defaults(h);
			cleanup(h, p, creative);
		}
		h.succeed();
	}

	@GameTest(environment = TestSupport.GLOBAL_STATE)
	public void modeOffStopsEventsAndClearsQueues(GameTestHelper h) throws CommandSyntaxException {
		defaults(h);
		ServerPlayer p = survivalPlayer(h);
		try {
			TestSupport.start(h, "speed_demon", p);
			p.giveExperienceLevels(2);
			h.assertValueEqual(manager(h).observe(p, true), 2, "queued");
			run(h, op(h), "chaosaholic off");
			h.assertTrue(manager(h).activeFor(p).isEmpty(), "events stopped by /chaosaholic off");
			h.assertValueEqual(manager(h).pending(p), 0, "queue cleared");
			h.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "effects reverted");
		} finally {
			defaults(h);
			cleanup(h, p);
		}
		h.succeed();
	}

	/** Settings codec: mode, scope and overrides survive a save/load; absent file = defaults (OFF, player). */
	@GameTest
	public void settingsRoundTrip(GameTestHelper h) {
		ChaosSettings fresh = new ChaosSettings();
		h.assertFalse(fresh.enabled(), "absent = off");
		h.assertValueEqual(fresh.scope(), Scope.PLAYER, "absent = player");
		fresh.setEnabled(true);
		fresh.setScope(Scope.WORLD);
		fresh.setEventEnabled(event("tiny_world"), false);
		fresh.setWeight(event("speed_demon"), 7);
		Tag tag = ChaosSettings.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
		ChaosSettings back = ChaosSettings.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		h.assertTrue(back.enabled(), "enabled");
		h.assertValueEqual(back.scope(), Scope.WORLD, "scope");
		h.assertFalse(back.isEventEnabled(event("tiny_world")), "event switch");
		h.assertValueEqual(back.weight(event("speed_demon")), 7, "weight");
		h.assertValueEqual(back.weight(event("feather_fall")), 100, "default weight");
		h.succeed();
	}

	/** Create World handoff: a pending value on the storage access wins and is consumed once. */
	@GameTest(environment = TestSupport.GLOBAL_STATE)
	public void pendingWorldModeIsConsumedOnce(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		PendingWorldMode access = (PendingWorldMode) ((MinecraftServerAccessor) server).chaosaholic$getStorageSource();
		try {
			access.chaosaholic$setPendingMode(false);
			ModeBootstrap.onServerStarting(server);
			h.assertFalse(settings(h).enabled(), "pending OFF applied");
			h.assertTrue(access.chaosaholic$takePendingMode() == null, "consumed");
			access.chaosaholic$setPendingMode(true);
			ModeBootstrap.onServerStarting(server);
			h.assertTrue(settings(h).enabled(), "pending ON applied");
			settings(h).setEnabled(false);
			ModeBootstrap.onServerStarting(server); // existing settings.dat, nothing pending: unchanged
			h.assertFalse(settings(h).enabled(), "existing value kept");
		} finally {
			defaults(h);
		}
		h.succeed();
	}

	/** Every registered event (except the gametest-only test_*) has name, desc, announce (and warning) in en_us. */
	@GameTest
	public void everyRegisteredEventHasLang(GameTestHelper h) {
		int count = 0;
		for (ChaosEvent e : EventRegistry.all()) {
			if (e.id().startsWith("test_")) continue;
			count++;
			for (String key : new String[] {EventIds.nameKey(e.id()), EventIds.descKey(e.id()), EventIds.announceKey(e.id())}) {
				h.assertTrue(Texts.english(key) != null, "missing " + key);
			}
			h.assertValueEqual(Texts.english(EventIds.warningKey(e.id())) != null, e.hasWarning(), "warning key of " + e.id());
			h.assertTrue(e.isInstant() || e.maxTicks() <= 180 * 20, "duration cap " + e.id());
		}
		h.assertValueEqual(count, 30, "registered events");
		h.succeed();
	}
}
