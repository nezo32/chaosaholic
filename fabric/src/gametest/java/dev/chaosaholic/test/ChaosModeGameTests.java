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
import dev.chaosaholic.core.EventIds;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventRegistry;
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
import net.minecraft.world.level.GameType;

/**
 * /chaosaholic commands, per-world settings (SavedData), the Create World handoff and the lang keys of every
 * registered event. Settings are server-global: every test here is synchronous and ends with {@code defaults}.
 */
public class ChaosModeGameTests {
	private static int run(GameTestHelper h, CommandSourceStack source, String command) throws CommandSyntaxException {
		CommandDispatcher<CommandSourceStack> d = h.getLevel().getServer().getCommands().getDispatcher();
		return d.execute(command, source);
	}

	private static CommandSourceStack op(GameTestHelper h) {
		return h.getLevel().getServer().createCommandSourceStack();
	}

	@GameTest
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
			h.assertValueEqual(run(h, op(h), "chaosaholic trigger midas_hour " + p.getGameProfile().name()), 0, "unimplemented event refused");
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

	@GameTest
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
	@GameTest
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
