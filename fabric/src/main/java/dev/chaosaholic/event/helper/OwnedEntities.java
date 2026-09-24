package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Entities an event instance spawned (TNT, mobs, bees, sheep, ...) or swapped in for another entity (a chicken
 * standing in for a zombie). Capped per instance ({@link ChaosLimits#MAX_OWNED_PER_EVENT}) and in total
 * ({@link ChaosLimits#MAX_OWNED_TOTAL}). Every owned entity carries the {@link Marks#OWNED_TAG} tag and an
 * {@link Marks.OwnerMark} attachment.
 *
 * <p>Cleanup: at the end of the instance every owned entity is discarded (replacements are turned back into the
 * original). Plain spawned entities are never saved to disk, so a chunk unload or a crash simply drops them;
 * replacements are saved with the original's data and restored when loaded without a running owner.
 *
 * <p>No unowned side spawns: {@link #spawn} adds the entity together with its passengers (a spider jockey's
 * skeleton) and owns all of them; owned zombies never call reinforcements; {@link #spawnMob} runs
 * {@code finalizeSpawn} without chicken jockeys; a mob converting (zombie drowning, slime splitting) passes
 * ownership on to what it became.
 */
public final class OwnedEntities {
	private final ActiveEvent owner;
	private final Map<UUID, Entity> entities = new LinkedHashMap<>();

	public OwnedEntities(ActiveEvent owner) {
		this.owner = owner;
	}

	/** True if another entity may be spawned now (both caps). */
	public boolean canSpawn() {
		return entities.size() < ChaosLimits.MAX_OWNED_PER_EVENT && EventManager.ownedTotal() < ChaosLimits.MAX_OWNED_TOTAL;
	}

	/**
	 * Tags {@code entity} (and its passengers) as owned and adds it to the instance's level. Position it first. Returns
	 * null (and adds nothing) when a cap is reached or the level refuses the entity. If {@code finalizeSpawn} already
	 * sat the entity on a vehicle that is in the level, a vehicle created this tick (a jockey chicken) is owned too,
	 * an older one (somebody's chicken) is left alone and the entity dismounts. Prefer {@link #spawnMob} for mobs.
	 */
	public <T extends Entity> @Nullable T spawn(T entity) {
		if (entity instanceof Player || !canSpawn()) return null;
		ServerLevel level = owner.level();
		Entity vehicle = entity.getVehicle() == null ? null : entity.getRootVehicle();
		if (vehicle != null && vehicle != entity && isInLevel(level, vehicle)) {
			if (vehicle.tickCount == 0 && !(vehicle instanceof Player)) {
				own(vehicle); // created by finalizeSpawn in this tick: a side spawn of ours
			} else {
				entity.stopRiding();
				if (vehicle instanceof Chicken chicken && !chicken.isVehicle()) chicken.setChickenJockey(false);
			}
		}
		List<Entity> all = entity.getSelfAndPassengers().toList();
		for (Entity e : all) if (e instanceof Player) return null;
		for (Entity e : all) {
			mark(e, Optional.empty());
			calm(e);
		}
		if (!level.tryAddFreshEntityWithPassengers(entity) || entity.isRemoved()) {
			for (Entity e : all) unmark(e);
			return null;
		}
		for (Entity e : all) if (!e.isRemoved()) entities.put(e.getUUID(), e);
		return entity;
	}

	/**
	 * Spawns a mob the vanilla way without side spawns: {@code finalizeSpawn} (equipment, difficulty bonuses; zombies
	 * without chicken jockeys) at the mob's current position, then {@link #spawn}. Position the mob first
	 * ({@code snapTo}). Returns null like {@link #spawn}.
	 */
	public <T extends Mob> @Nullable T spawnMob(T mob, EntitySpawnReason reason) {
		if (!canSpawn()) return null;
		prepare(owner.level(), mob, reason);
		return spawn(mob);
	}

	/**
	 * {@code mob.finalizeSpawn} with group data that forbids side spawns: a zombie (also husk, drowned, zombified
	 * piglin, zombie villager) never brings a chicken jockey. For events that finalize a mob themselves before
	 * {@link #spawn}.
	 */
	public static void prepare(ServerLevel level, Mob mob, EntitySpawnReason reason) {
		SpawnGroupData data = mob instanceof Zombie ? new Zombie.ZombieGroupData(Zombie.getSpawnAsBabyOdds(level.getRandom()), false) : null;
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, data);
	}

	/**
	 * Takes ownership of an entity that is already in the instance's level (e.g. what {@code FallingBlockEntity.fall}
	 * returns, which adds itself), with its passengers. Returns false (and leaves it alone) when a cap is reached.
	 */
	public boolean adopt(Entity entity) {
		if (entity instanceof Player || entity.isRemoved() || entity.level() != owner.level() || !canSpawn()) return false;
		for (Entity e : entity.getSelfAndPassengers().toList()) {
			if (e instanceof Player || e.isRemoved()) continue;
			own(e);
		}
		return true;
	}

	/**
	 * Replaces {@code original} with {@code replacement} (placed at the original's position and rotation) and keeps
	 * the original's full saved data on the replacement, so the end of the event - or a later load after a crash -
	 * brings the original back where the replacement is. Refuses players, passengers, vehicles and entities that
	 * cannot be saved. Returns null if nothing was changed.
	 */
	public <T extends Entity> @Nullable T replace(Entity original, T replacement) {
		if (original instanceof Player || original.isPassenger() || original.isVehicle() || original.isRemoved() || !canSpawn()) return null;
		if (original.level() != owner.level()) return null;
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, original.registryAccess());
		if (!original.save(out)) return null;
		CompoundTag saved = out.buildResult();
		replacement.snapTo(original.getX(), original.getY(), original.getZ(), original.getYRot(), original.getXRot());
		mark(replacement, Optional.of(saved));
		calm(replacement);
		if (!owner.level().addFreshEntity(replacement)) {
			unmark(replacement);
			return null;
		}
		original.discard();
		entities.put(replacement.getUUID(), replacement);
		return replacement;
	}

	/** Live owned entities of this instance. */
	public List<Entity> list() {
		List<Entity> out = new ArrayList<>();
		for (Entity e : entities.values()) if (!e.isRemoved()) out.add(e);
		return out;
	}

	/** Number of live owned entities. */
	public int count() {
		int n = 0;
		for (Entity e : entities.values()) if (!e.isRemoved()) n++;
		return n;
	}

	public boolean owns(Entity entity) {
		return entities.get(entity.getUUID()) == entity;
	}

	/** Reverts one owned entity now (discard, or restore the original). */
	public void remove(Entity entity) {
		if (entities.remove(entity.getUUID()) != null) revert(entity);
	}

	/** Framework: end of the instance. */
	public void revertAll() {
		List<Entity> all = new ArrayList<>(entities.values());
		entities.clear();
		for (Entity e : all) revert(e);
	}

	/** Framework: the entity unloaded (its saved copy, if any, is handled on the next load). */
	public void forget(Entity entity) {
		entities.remove(entity.getUUID(), entity);
	}

	/** Framework: an owned entity of this (running) instance was loaded again. */
	public void readopt(Entity entity) {
		entities.put(entity.getUUID(), entity);
	}

	private void own(Entity entity) {
		mark(entity, Optional.empty());
		calm(entity);
		entities.put(entity.getUUID(), entity);
	}

	private void mark(Entity entity, Optional<CompoundTag> restore) {
		entity.addTag(Marks.OWNED_TAG);
		entity.setAttached(Marks.OWNER, new Marks.OwnerMark(owner.uuid(), restore));
	}

	private static void unmark(Entity entity) {
		entity.removeAttached(Marks.OWNER);
		entity.removeTag(Marks.OWNED_TAG);
	}

	/** No reinforcements: a zombie hit by a player on Hard would otherwise call unowned zombies. */
	private static void calm(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return;
		AttributeInstance reinforcements = living.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements == null) return;
		reinforcements.removeModifiers();
		reinforcements.setBaseValue(0.0);
	}

	private static boolean isInLevel(ServerLevel level, Entity entity) {
		return !entity.isRemoved() && entity.level() == level && level.getEntity(entity.getUUID()) == entity;
	}

	/** True for entities that must never be written to disk: owned, without an original to restore (EntityMixin). */
	public static boolean isUnsaved(Entity entity) {
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		return mark != null && mark.restore().isEmpty();
	}

	/**
	 * Discards {@code entity}, or turns it back into the original it replaced. If the original cannot be restored
	 * (corrupt data, the level refuses it), the replacement stays as a normal entity (mark and tag removed) rather
	 * than losing both; a warning is logged. A dead (dying) replacement is just discarded: the original died with it.
	 */
	public static void revert(Entity entity) {
		if (entity.isRemoved()) return;
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		// a replacement that died (killed during the event, still in its death animation) took the original with it
		boolean dead = entity instanceof LivingEntity living && living.isDeadOrDying();
		if (mark != null && mark.restore().isPresent() && !dead && entity.level() instanceof ServerLevel level) {
			if (!restore(level, entity, mark.restore().get())) {
				unmark(entity);
				Chaosaholic.LOGGER.warn("Could not restore the entity replaced by {} after a chaos event; keeping the replacement", entity);
				return;
			}
		}
		entity.discard();
	}

	/** Adds the original back at the replacement's position. False (nothing added) if that failed. */
	private static boolean restore(ServerLevel level, Entity at, CompoundTag saved) {
		try {
			Entity original = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
					level, EntitySpawnReason.LOAD, e -> {
						e.snapTo(at.getX(), at.getY(), at.getZ(), at.getYRot(), at.getXRot());
						return e;
					});
			return original != null && level.tryAddFreshEntityWithPassengers(original) && !original.isRemoved();
		} catch (RuntimeException e) {
			Chaosaholic.LOGGER.warn("Could not restore an entity replaced by a chaos event", e);
			return false;
		}
	}

	/**
	 * ServerEntityEvents.ENTITY_LOAD: an owned entity whose owner still runs is adopted again; otherwise (event over,
	 * server restarted after a crash) it is reverted on the next tick (not while its chunk is loading).
	 */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		if (mark == null) {
			if (entity.entityTags().contains(Marks.OWNED_TAG)) EventManager.defer(entity::discard); // tag without data: leftover
			return;
		}
		ActiveEvent live = EventManager.findLive(mark.owner());
		if (live != null && live.level() == level) {
			live.entities().readopt(entity);
		} else {
			EventManager.defer(() -> revert(entity));
		}
	}

	/**
	 * ServerLivingEntityEvents.AFTER_DEATH of any non-player: a replacement that dies drops its snapshot (the original
	 * dies with it: it is never restored, neither at the end nor after a reload); it stays owned, so it is discarded
	 * at the end and never saved.
	 */
	public static void onDeath(LivingEntity entity) {
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		if (mark == null || mark.restore().isEmpty()) return;
		entity.setAttached(Marks.OWNER, new Marks.OwnerMark(mark.owner(), Optional.empty()));
	}

	/**
	 * ServerLivingEntityEvents.MOB_CONVERSION: what an owned mob turns into (zombie → drowned, slime → smaller
	 * slimes, a replacement struck by lightning) inherits its mark, so it is removed (or turned back into the
	 * original) at the end too and is never saved as a stray mob.
	 */
	public static void onConversion(Mob previous, Mob converted) {
		Marks.OwnerMark mark = previous.getAttached(Marks.OWNER);
		if (mark == null) return;
		converted.addTag(Marks.OWNED_TAG);
		converted.setAttached(Marks.OWNER, mark);
		calm(converted);
		ActiveEvent live = EventManager.findLive(mark.owner());
		if (live != null && !live.isStopped()) {
			live.entities().readopt(converted);
		} else {
			EventManager.defer(() -> revert(converted));
		}
	}
}
