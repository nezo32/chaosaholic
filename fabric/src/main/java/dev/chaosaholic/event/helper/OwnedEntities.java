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
	 * Tags {@code entity} as owned and adds it to the instance's level. Position it first. Returns null (and adds
	 * nothing) when a cap is reached or the level refuses the entity.
	 */
	public <T extends Entity> @Nullable T spawn(T entity) {
		if (entity instanceof Player || !canSpawn()) return null;
		mark(entity, Optional.empty());
		if (!owner.level().addFreshEntity(entity)) return null;
		entities.put(entity.getUUID(), entity);
		return entity;
	}

	/**
	 * Takes ownership of an entity that is already in the instance's level (e.g. what {@code FallingBlockEntity.fall}
	 * returns, which adds itself). Returns false (and leaves it alone) when a cap is reached.
	 */
	public boolean adopt(Entity entity) {
		if (entity instanceof Player || entity.isRemoved() || entity.level() != owner.level() || !canSpawn()) return false;
		mark(entity, Optional.empty());
		entities.put(entity.getUUID(), entity);
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
		if (!owner.level().addFreshEntity(replacement)) {
			replacement.removeAttached(Marks.OWNER);
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

	private void mark(Entity entity, Optional<CompoundTag> restore) {
		entity.addTag(Marks.OWNED_TAG);
		entity.setAttached(Marks.OWNER, new Marks.OwnerMark(owner.uuid(), restore));
	}

	/** True for entities that must never be written to disk: owned, without an original to restore (EntityMixin). */
	public static boolean isUnsaved(Entity entity) {
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		return mark != null && mark.restore().isEmpty();
	}

	/** Discards {@code entity}, or turns it back into the original it replaced. */
	public static void revert(Entity entity) {
		Marks.OwnerMark mark = entity.getAttached(Marks.OWNER);
		if (entity.isRemoved()) return;
		if (mark != null && mark.restore().isPresent() && entity.level() instanceof ServerLevel level) {
			restore(level, entity, mark.restore().get());
		}
		entity.discard();
	}

	private static void restore(ServerLevel level, Entity at, CompoundTag saved) {
		try {
			Entity original = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
					level, EntitySpawnReason.LOAD, e -> {
						e.snapTo(at.getX(), at.getY(), at.getZ(), at.getYRot(), at.getXRot());
						return e;
					});
			if (original == null) return;
			at.discard(); // free the position (and never keep two copies)
			if (!level.tryAddFreshEntityWithPassengers(original)) {
				Chaosaholic.LOGGER.warn("Could not restore {} replaced by a chaos event", original);
			}
		} catch (RuntimeException e) {
			Chaosaholic.LOGGER.warn("Could not restore an entity replaced by a chaos event", e);
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
}
