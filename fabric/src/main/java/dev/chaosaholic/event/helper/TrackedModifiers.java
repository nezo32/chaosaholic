package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.chaosaholic.Chaosaholic;
import dev.chaosaholic.event.ActiveEvent;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Attribute modifiers for the run (scale, gravity, speed, ...). Always transient: vanilla never saves transient
 * modifiers, so a crash or a chunk unload cannot leave one behind. The modifier id is
 * {@code chaosaholic:event/<id>/<instance uuid>}, one per instance and attribute.
 */
public final class TrackedModifiers {
	private record Entry(LivingEntity entity, Holder<Attribute> attribute) {}

	private final ActiveEvent owner;
	private final List<Entry> entries = new ArrayList<>();
	private final Identifier id;

	public TrackedModifiers(ActiveEvent owner) {
		this.owner = owner;
		this.id = Identifier.fromNamespaceAndPath(Chaosaholic.MOD_ID, "event/" + owner.id() + "/" + owner.uuid());
	}

	/** The modifier id of this instance. */
	public Identifier id() {
		return id;
	}

	/**
	 * Adds (or updates) this instance's modifier on {@code attribute}. Returns false if the entity has no such
	 * attribute. Example: {@code add(mob, Attributes.SCALE, -0.5, Operation.ADD_MULTIPLIED_TOTAL)} halves the size.
	 */
	public boolean add(LivingEntity entity, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return false;
		instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		boolean known = false;
		for (Entry e : entries) if (e.entity() == entity && e.attribute().equals(attribute)) known = true;
		if (!known) entries.add(new Entry(entity, attribute));
		return true;
	}

	public boolean tracks(Entity entity) {
		for (Entry e : entries) if (e.entity() == entity) return true;
		return false;
	}

	/** Entities that currently carry a modifier of this instance. */
	public List<LivingEntity> entities() {
		List<LivingEntity> out = new ArrayList<>();
		for (Entry e : entries) if (!out.contains(e.entity())) out.add(e.entity());
		return out;
	}

	/** Framework (and events): removes this instance's modifiers from {@code entity}. */
	public void revert(Entity entity) {
		for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
			Entry e = it.next();
			if (e.entity() != entity) continue;
			it.remove();
			remove(e);
		}
	}

	public void revertAll() {
		List<Entry> all = new ArrayList<>(entries);
		entries.clear();
		for (Entry e : all) remove(e);
	}

	/** Framework: the entity unloaded (transient modifiers were not saved with it). */
	public void forget(Entity entity) {
		entries.removeIf(e -> e.entity() == entity);
	}

	private void remove(Entry e) {
		AttributeInstance instance = e.entity().getAttribute(e.attribute());
		if (instance != null) instance.removeModifier(id);
	}
}
