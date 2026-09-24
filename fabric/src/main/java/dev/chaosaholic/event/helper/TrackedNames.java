package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.EventManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Temporary custom names (e.g. "Dinnerbone" to flip mobs). The original name and visibility are stored in a
 * persistent {@link Marks.NameMark} on the entity, so they come back at the end of the event, after a chunk reload
 * or after a crash.
 */
public final class TrackedNames {
	private final ActiveEvent owner;
	private final List<Entity> entities = new ArrayList<>();

	public TrackedNames(ActiveEvent owner) {
		this.owner = owner;
	}

	/**
	 * Sets a temporary custom name. The name itself is not shown to players unless {@code visible}; pass a
	 * translatable or a name the game recognizes (Dinnerbone, Grumm, jeb_). Refuses players and entities already
	 * renamed by another event.
	 */
	public boolean rename(Entity entity, Component name, boolean visible) {
		if (entity instanceof Player || entity.hasAttached(Marks.NAME)) return false;
		entity.setAttached(Marks.NAME, new Marks.NameMark(owner.uuid(), Optional.ofNullable(entity.getCustomName()), entity.isCustomNameVisible()));
		entity.setCustomName(name);
		entity.setCustomNameVisible(visible);
		entities.add(entity);
		return true;
	}

	public boolean tracks(Entity entity) {
		return entities.contains(entity);
	}

	public void revert(Entity entity) {
		if (entities.remove(entity)) restore(entity);
	}

	public void revertAll() {
		List<Entity> all = new ArrayList<>(entities);
		entities.clear();
		for (Entity e : all) restore(e);
	}

	public void forget(Entity entity) {
		entities.remove(entity);
	}

	public void readopt(Entity entity) {
		if (!entities.contains(entity)) entities.add(entity);
	}

	/** Puts the stored original name back and removes the mark. */
	public static void restore(Entity entity) {
		Marks.NameMark mark = entity.removeAttached(Marks.NAME);
		if (mark == null) return;
		entity.setCustomName(mark.name().orElse(null));
		entity.setCustomNameVisible(mark.visible());
	}

	/** ServerEntityEvents.ENTITY_LOAD: re-adopt for a running owner, else restore on the next tick. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		Marks.NameMark mark = entity.getAttached(Marks.NAME);
		if (mark == null) return;
		ActiveEvent live = EventManager.findLive(mark.owner());
		if (live != null && live.level() == level) {
			live.names().readopt(entity);
		} else {
			EventManager.defer(() -> restore(entity));
		}
	}
}
