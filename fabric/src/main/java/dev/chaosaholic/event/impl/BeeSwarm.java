package dev.chaosaholic.event.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventContext;
import dev.chaosaholic.event.RemoveReason;
import dev.chaosaholic.event.StopReason;
import dev.chaosaholic.event.helper.Spots;
import dev.chaosaholic.event.helper.Warning;
import dev.chaosaholic.event.impl.mob.NoLoot;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: a few angry bees chase each affected player for 30-45 s. After the warning (3 s, 4 s on Hardcore,
 * {@link Warning}) {@link #swarmSize swarm size} bees (Easy 2, Normal 3, Hard/Hardcore {@link #MAX_BEES}) appear
 * {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks away, angry at that player (re-angered every second so vanilla's
 * anger timer never runs out during the run). Refused on Peaceful (bees would do no damage there).
 *
 * <p>Vanilla bee rules stay: each bee stings once (2 damage, poison on Normal/Hard, which never kills: vanilla poison
 * stops at half a heart) and then calms down. On Hardcore a sting that would kill is cancelled (the bee keeps trying,
 * harmlessly, until the player heals or the event ends). Instead of the vanilla slow death after stinging, a bee that has stung is removed with a puff within a
 * second: nothing dies, nothing is left behind. The bees never enter hives (they would be saved inside the hive), drop
 * no experience ({@link NoLoot}) and are owned by the instance: never saved, removed at the end, and a player's
 * swarm leaves with that player (logout, death, Creative, dimension change). An extension sends a fresh swarm, at
 * most {@link #MAX_BEES} bees alive per player.
 */
public final class BeeSwarm extends ChaosEvent {
	/** Bees alive per player, at most (also the Hard swarm size). */
	public static final int MAX_BEES = 4;
	/** Horizontal spawn distance from the player, in blocks. */
	public static final double MIN_DISTANCE = 4.0;
	public static final double MAX_DISTANCE = 7.0;
	/** Bees appear this far above the chosen floor spot (they fly). */
	private static final double HOVER = 1.5;

	/** Per instance: which player each bee chases (bee uuid → player uuid). */
	private static final class State {
		private final Map<UUID, UUID> targets = new HashMap<>();
	}

	public BeeSwarm() {
		super("bee_swarm", Category.BAD, 30, 45);
	}

	/** Bees per player: Easy 2, Normal 3, Hard (and Hardcore) 4; Peaceful 0. */
	public static int swarmSize(Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> 2;
			case NORMAL -> 3;
			case HARD -> MAX_BEES;
		};
	}

	@Override
	public boolean canStart(EventContext ctx) {
		if (ctx.isPeaceful()) return false;
		return Spots.near(ctx.level(), ctx.trigger().position(), MIN_DISTANCE, MAX_DISTANCE, ctx.random(), EntityTypes.BEE).isPresent();
	}

	@Override
	public void onStart(ActiveEvent ev) {
		Warning.thenRun(ev, () -> release(ev));
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.FALLING_NECTAR, player.getX(), player.getY() + 1.5, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void onExtended(ActiveEvent ev, int addedTicks) {
		Warning.thenRun(ev, () -> release(ev));
	}

	private void release(ActiveEvent ev) {
		State state = ev.state(State::new);
		int size = swarmSize(ev.context().difficulty());
		for (ServerPlayer p : ev.players()) {
			int alive = 0;
			for (Entity e : ev.entities().list()) if (p.getUUID().equals(state.targets.get(e.getUUID()))) alive++;
			for (int i = alive; i < Math.min(size, MAX_BEES); i++) {
				Bee bee = spawnBee(ev, p);
				if (bee == null) break;
				state.targets.put(bee.getUUID(), p.getUUID());
			}
		}
	}

	private @Nullable Bee spawnBee(ActiveEvent ev, ServerPlayer player) {
		if (!ev.entities().canSpawn()) return null;
		Optional<Vec3> spot = Spots.near(ev.level(), player.position(), MIN_DISTANCE, MAX_DISTANCE, ev.random(), EntityTypes.BEE);
		if (spot.isEmpty()) return null;
		Bee bee = EntityTypes.BEE.create(ev.level(), EntitySpawnReason.EVENT);
		if (bee == null) return null;
		Vec3 s = spot.get();
		bee.snapTo(s.x, s.y + HOVER, s.z, ev.random().nextFloat() * 360.0F, 0.0F);
		NoLoot.apply(bee);
		bee.setPersistenceRequired();
		bee.setStayOutOfHiveCountdown(Integer.MAX_VALUE);
		if (ev.entities().spawn(bee) == null) return null;
		anger(bee, player);
		ev.level().sendParticles(ParticleTypes.FALLING_NECTAR, bee.getX(), bee.getY() + 0.3, bee.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		return bee;
	}

	private static void anger(Bee bee, ServerPlayer player) {
		bee.setPersistentAngerTarget(EntityReference.of(player));
		bee.startPersistentAngerTimer();
		bee.setTarget(player);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (!ev.every(ChaosLimits.TICKS_PER_SECOND)) return;
		State state = ev.state(State::new);
		for (Entity e : ev.entities().list()) {
			if (!(e instanceof Bee bee)) continue;
			if (bee.hasStung()) {
				puff(ev, bee);
				ev.entities().remove(bee);
				state.targets.remove(bee.getUUID());
				continue;
			}
			ServerPlayer target = chased(ev, state, bee);
			if (target != null && bee.getTarget() != target) anger(bee, target);
			else if (target != null) bee.startPersistentAngerTimer(); // keep the anger up for the whole run
		}
	}

	private static @Nullable ServerPlayer chased(ActiveEvent ev, State state, Bee bee) {
		UUID id = state.targets.get(bee.getUUID());
		if (id == null) return null;
		for (ServerPlayer p : ev.players()) if (p.getUUID().equals(id)) return p;
		return null;
	}

	@Override
	public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
		if (reason == RemoveReason.EVENT_ENDED) return;
		State state = ev.state(State::new);
		for (Entity e : List.copyOf(ev.entities().list())) {
			if (player.getUUID().equals(state.targets.get(e.getUUID()))) {
				puff(ev, e);
				ev.entities().remove(e);
				state.targets.remove(e.getUUID());
			}
		}
	}

	/** Hardcore: a sting of this instance never kills a player (poison cannot kill anyway). */
	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !ev.context().isHardcore() || !isLethalSting(ev, entity, source, amount);
	}

	/** A sting by a bee of this instance that would kill a player (cancelled on Hardcore). */
	public static boolean isLethalSting(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer)) return false;
		Entity direct = source.getDirectEntity();
		return direct instanceof Bee && ev.entities().owns(direct) && TntRain.isLethal(entity, amount);
	}

	@Override
	public void onStop(ActiveEvent ev, StopReason reason) {
		for (Entity e : ev.entities().list()) puff(ev, e); // the framework removes them right after
	}

	private static void puff(ActiveEvent ev, Entity e) {
		ev.level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.3, e.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEEHIVE_EXIT);
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every wave
	}
}
