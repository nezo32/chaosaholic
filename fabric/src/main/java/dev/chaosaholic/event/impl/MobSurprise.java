package dev.chaosaholic.event.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bad: a wave of hostile mobs per affected player for 60-90 s. The spawn spots are chosen at the start and marked
 * with TRIAL_OMEN particles during the warning (3 s, 4 s on Hardcore, {@link Warning}); then
 * {@link #waveSize wave size} mobs (Easy 2, Normal 3, Hard/Hardcore 4) appear there, 8-12 blocks away on safe spots
 * (sturdy floor, room for the mob, never within {@link #MIN_PLAYER_DISTANCE} blocks of any player) and target that
 * player. Refused on Peaceful and when no safe spot exists.
 *
 * <p>Mob types: zombie, skeleton, spider; in daylight under the open sky husk or spider (no instant burning). No
 * creepers: their explosions would change the world permanently. Jockeys and baby zombies are suppressed.
 *
 * <p>The mobs are owned by the instance (never saved, so a chunk unload or crash drops them) and discarded at the end
 * if still alive; a player's own wave also vanishes when that player leaves the event early (logout, death, Creative,
 * dimension change). No farming: they drop no loot, no equipment and no experience, cannot pick items up and call no
 * zombie reinforcements ({@link NoLoot}). An extension (rolled again) sends another wave after another warning; at
 * most {@link #MAX_ALIVE} of them are alive per instance.
 */
public final class MobSurprise extends ChaosEvent {
	/** Horizontal spawn distance from the player, in blocks. */
	public static final double MIN_DISTANCE = 8.0;
	public static final double MAX_DISTANCE = 12.0;
	/** A spawn spot is dropped (and searched again) if any player is closer than this when the wave comes. */
	public static final double MIN_PLAYER_DISTANCE = 6.0;
	/** Live wave mobs per instance (extensions add waves up to this). */
	public static final int MAX_ALIVE = 12;

	private static final List<EntityType<? extends Mob>> NIGHT_TYPES = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER);
	private static final List<EntityType<? extends Mob>> DAY_TYPES = List.of(EntityTypes.HUSK, EntityTypes.SPIDER);

	/** Per instance: which player each spawned mob hunts (mob uuid → player uuid). */
	private static final class State {
		private final Map<UUID, UUID> targets = new HashMap<>();
	}

	public MobSurprise() {
		super("mob_surprise", Category.BAD, 60, 90);
	}

	/** Mobs per player and wave: Easy 2, Normal 3, Hard (and Hardcore, which is Hard) 4; Peaceful 0. */
	public static int waveSize(Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> 2;
			case NORMAL -> 3;
			case HARD -> 4;
		};
	}

	@Override
	public boolean canStart(EventContext ctx) {
		if (ctx.isPeaceful()) return false;
		ServerPlayer p = ctx.trigger();
		return Spots.near(ctx.level(), p.position(), MIN_DISTANCE, MAX_DISTANCE, ctx.random(), EntityTypes.ZOMBIE).isPresent();
	}

	@Override
	public void onStart(ActiveEvent ev) {
		announceWave(ev, ev.context().players());
	}

	@Override
	public void onExtended(ActiveEvent ev, int addedTicks) {
		announceWave(ev, ev.players());
	}

	/** Picks the spots now, marks them during the warning and spawns the wave when it runs out. */
	private void announceWave(ActiveEvent ev, List<ServerPlayer> players) {
		int size = waveSize(ev.context().difficulty());
		Map<UUID, List<Vec3>> plan = new LinkedHashMap<>();
		for (ServerPlayer p : players) {
			List<Vec3> spots = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				Spots.near(ev.level(), p.position(), MIN_DISTANCE, MAX_DISTANCE, ev.random(), EntityTypes.ZOMBIE).ifPresent(spots::add);
			}
			plan.put(p.getUUID(), spots);
		}
		int steps = Warning.delay(ev.context()) / ChaosLimits.TICKS_PER_SECOND;
		for (int i = 0; i < steps; i++) {
			ev.schedule(i * ChaosLimits.TICKS_PER_SECOND + 1, () -> {
				for (List<Vec3> spots : plan.values()) {
					for (Vec3 s : spots) ev.level().sendParticles(ParticleTypes.TRIAL_OMEN, s.x, s.y + 0.8, s.z, 8, 0.3, 0.6, 0.3, 0.02);
				}
			});
		}
		Warning.thenRun(ev, () -> {
			for (ServerPlayer p : ev.players()) {
				List<Vec3> spots = plan.get(p.getUUID());
				if (spots != null) spawnWave(ev, p, spots);
			}
		});
	}

	private void spawnWave(ActiveEvent ev, ServerPlayer player, List<Vec3> spots) {
		State state = ev.state(State::new);
		for (Vec3 planned : spots) {
			if (ev.entities().count() >= MAX_ALIVE || !ev.entities().canSpawn()) return;
			Mob mob = spawnOne(ev, player, planned);
			if (mob != null) state.targets.put(mob.getUUID(), player.getUUID());
		}
	}

	private @Nullable Mob spawnOne(ActiveEvent ev, ServerPlayer player, Vec3 planned) {
		ServerLevel level = ev.level();
		boolean sunny = level.isBrightOutside() && level.canSeeSky(BlockPos.containing(planned));
		List<EntityType<? extends Mob>> types = sunny ? DAY_TYPES : NIGHT_TYPES;
		EntityType<? extends Mob> type = types.get(ev.random().nextInt(types.size()));
		Vec3 spot = usable(level, planned, type) ? planned : null;
		if (spot == null) {
			Optional<Vec3> again = Spots.near(level, player.position(), MIN_DISTANCE, MAX_DISTANCE, ev.random(), type);
			if (again.isEmpty() || !usable(level, again.get(), type)) return null;
			spot = again.get();
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) return null; // Peaceful switched on meanwhile
		float yaw = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		mob.setYHeadRot(yaw);
		SpawnGroupData data = mob instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null; // adult, no chicken jockey
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, data);
		mob.ejectPassengers(); // spider jockey riders are never added to the level
		NoLoot.apply(mob);
		mob.setPersistenceRequired(); // no natural despawn during the run; owned entities are never saved anyway
		if (ev.entities().spawn(mob) == null) return null;
		mob.setTarget(player);
		level.sendParticles(ParticleTypes.TRIAL_OMEN, spot.x, spot.y + 0.8, spot.z, 16, 0.4, 0.8, 0.4, 0.05);
		level.sendParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 8, 0.3, 0.4, 0.3, 0.02);
		return mob;
	}

	/** Safe for {@code type} and not next to any player (they may have walked there during the warning). */
	private static boolean usable(ServerLevel level, Vec3 spot, EntityType<?> type) {
		if (!Spots.isSafe(level, BlockPos.containing(spot), type)) return false;
		double min2 = MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
		for (Player p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(spot) < min2) return false;
		}
		return true;
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (!ev.every(ChaosLimits.TICKS_PER_SECOND)) return;
		State state = ev.state(State::new);
		for (Entity e : ev.entities().list()) {
			if (!(e instanceof Mob mob)) continue;
			ServerPlayer target = hunted(ev, state, mob);
			if (target != null && mob.getTarget() != target) mob.setTarget(target);
		}
	}

	private static @Nullable ServerPlayer hunted(ActiveEvent ev, State state, Mob mob) {
		UUID id = state.targets.get(mob.getUUID());
		if (id == null) return null;
		for (ServerPlayer p : ev.players()) if (p.getUUID().equals(id)) return p;
		return null;
	}

	/** A player who leaves the event early takes their wave along (the rest is discarded at the end anyway). */
	@Override
	public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
		if (reason == RemoveReason.EVENT_ENDED) return;
		State state = ev.state(State::new);
		for (Entity e : ev.entities().list()) {
			if (player.getUUID().equals(state.targets.get(e.getUUID()))) {
				ev.level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
				ev.entities().remove(e);
				state.targets.remove(e.getUUID());
			}
		}
	}

	@Override
	public void onStop(ActiveEvent ev, StopReason reason) {
		// the framework discards the survivors right after this; a puff shows where they went
		for (Entity e : ev.entities().list()) {
			ev.level().sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		}
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE);
	}

	@Override
	public boolean hasWarning() {
		return true; // Warning.thenRun(...) before every wave
	}
}
