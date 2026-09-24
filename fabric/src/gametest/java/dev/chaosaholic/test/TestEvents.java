package dev.chaosaholic.test;

import java.util.concurrent.atomic.AtomicInteger;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.EventRegistry;
import dev.chaosaholic.event.RemoveReason;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Area;
import net.fabricmc.api.ModInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * Test-only events (ids {@code test_*}), registered by the gametest mod only; never part of the real mod. They
 * exercise framework features that the three real reference events do not: instant events, owned entities,
 * temporary blocks, temporary names, replacements, refusals, the per-player cap and error handling.
 *
 * <p>Registered deterministically by the gametest mod's {@code main} entrypoint (before the server starts, so before
 * any test or roll), all with default weight 0: random rolls never pick them, even before {@link TestSupport#defaults}
 * switches them off. {@link #ensureRegistered} is kept as a no-op safety net.
 */
public final class TestEvents implements ModInitializer {
	public static final AtomicInteger INSTANT_RUNS = new AtomicInteger();
	public static final AtomicInteger BOOM_STOPS = new AtomicInteger();
	public static final AtomicInteger REMOVED_BY_LOGOUT = new AtomicInteger();

	private static boolean registered;

	/** Number of {@code test_slot_<n>} events: enough to fill every per-player slot. */
	public static final int SLOTS = ChaosLimits.MAX_ACTIVE_PER_PLAYER;

	/** Entrypoint "main" of the gametest mod. */
	public TestEvents() {}

	@Override
	public void onInitialize() {
		ensureRegistered();
	}

	public static synchronized void ensureRegistered() {
		if (registered) return;
		registered = true;
		EventRegistry.register(new Instant());
		EventRegistry.register(new Resources());
		EventRegistry.register(new Boom());
		EventRegistry.register(new Marker());
		EventRegistry.register(new Refuse());
		for (int i = 1; i <= SLOTS; i++) EventRegistry.register(new Slot(i));
	}

	/** Id of the {@code n}-th slot filler (1-based). */
	public static String slot(int n) {
		return "test_slot_" + n;
	}

	/** Timed, does nothing: fills one of a player's {@link ChaosLimits#MAX_ACTIVE_PER_PLAYER} slots. */
	static final class Slot extends ChaosEvent {
		Slot(int n) {
			super(slot(n), Category.WEIRD, 60, 60, 0);
		}
	}

	/** Timed, but canStart always refuses (like an event without a valid target). */
	static final class Refuse extends ChaosEvent {
		Refuse() {
			super("test_refuse", Category.GOOD, 60, 60, 0);
		}

		@Override
		public boolean canStart(EventContext ctx) {
			return false;
		}
	}

	/**
	 * Timed, gives affected players Glowing. Used by the world-scope tests: a world-scope start extends every running
	 * instance of the same event on any player of the dimension, so those tests use an event no other test runs.
	 */
	static final class Marker extends ChaosEvent {
		Marker() {
			super("test_marker", Category.WEIRD, 60, 60, 0);
		}

		@Override
		public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
			ev.effects().give(player, MobEffects.GLOWING, 0);
		}
	}

	/** Instant: counts runs; no boss bar, ends the tick it starts. */
	static final class Instant extends ChaosEvent {
		Instant() {
			super("test_instant", Category.WEIRD, INSTANT, INSTANT, 0);
		}

		@Override
		public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
			INSTANT_RUNS.incrementAndGet();
		}
	}

	/**
	 * Uses every tracker: spawns an owned zombie 2 blocks east of the player, places a temporary glass block 3 blocks
	 * above the player, renames pigs within 4 blocks and makes them glow, and replaces a pig standing 6 blocks north
	 * (if any) with a chicken.
	 */
	static final class Resources extends ChaosEvent {
		Resources() {
			super("test_resources", Category.WEIRD, 60, 60, 0);
		}

		@Override
		public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
			Zombie zombie = EntityTypes.ZOMBIE.create(ev.level(), EntitySpawnReason.TRIGGERED);
			if (zombie != null) {
				zombie.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
				zombie.setNoAi(true);
				ev.entities().spawn(zombie);
			}
			ev.blocks().set(player.blockPosition().above(3), Blocks.GLASS.defaultBlockState());
			for (LivingEntity mob : Area.entities(ev.level(), player.position(), 4, LivingEntity.class, e -> e instanceof Pig)) {
				ev.names().rename(mob, Component.literal("Dinnerbone"), false);
				ev.effects().give(mob, MobEffects.GLOWING, 0);
			}
			for (LivingEntity mob : Area.entities(ev.level(), player.position().add(0, 0, -6), 1.5, LivingEntity.class, e -> e instanceof Pig)) {
				var chicken = EntityTypes.CHICKEN.create(ev.level(), EntitySpawnReason.TRIGGERED);
				if (chicken != null) ev.entities().replace(mob, chicken);
			}
		}

		@Override
		public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
			if (reason == RemoveReason.LOGOUT) REMOVED_BY_LOGOUT.incrementAndGet();
		}
	}

	/** Throws from onTick on its third tick: the framework must stop it (ERROR) and still clean up. */
	static final class Boom extends ChaosEvent {
		Boom() {
			super("test_boom", Category.BAD, 60, 60, 0);
		}

		@Override
		public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
			ev.effects().give(player, MobEffects.SLOWNESS, 0);
			ev.blocks().set(player.blockPosition().above(4), Blocks.GLASS.defaultBlockState());
		}

		@Override
		public void onTick(ActiveEvent ev) {
			if (ev.age() == 3) throw new IllegalStateException("test_boom exploded on purpose");
		}

		@Override
		public void onStop(ActiveEvent ev, StopReason reason) {
			if (reason == StopReason.ERROR) BOOM_STOPS.incrementAndGet();
		}
	}
}
