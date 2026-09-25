package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.mixin.MobEffectInstanceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Mob effects an event keeps on entities for its whole run. Given with the instance's remaining time (plus a small
 * margin), so even a crash leaves at most that much behind; re-applied every {@link ChaosLimits#EFFECT_REFRESH_TICKS}
 * if removed (milk) or after an extension; removed when the entity stops being affected, unless something else
 * replaced it with a stronger or longer effect in the meantime (longer than anything this tracker gave).
 *
 * <p>Only this tracker's layer is taken away: a weaker, longer effect that vanilla kept hidden under ours (Swiftness I
 * potion under Speed III) comes back with its remaining time, and our effect hidden under a stronger foreign one is
 * unlinked so it never resurfaces. Players are accepted only while {@link ActiveEvent#mayChange} allows it
 * (affected, or eligible in the instance's dimension); a tracked player who stops being eligible, logs out, dies or
 * leaves the dimension is reverted by the framework.
 */
public final class TrackedEffects {
	private static final class Entry {
		private final LivingEntity entity;
		private final Holder<MobEffect> effect;
		private final int amplifier;
		/** Longest duration this tracker ever gave: a current effect longer than that is not ours. */
		private int applied;

		private Entry(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
			this.entity = entity;
			this.effect = effect;
			this.amplifier = amplifier;
		}

		LivingEntity entity() {
			return entity;
		}

		Holder<MobEffect> effect() {
			return effect;
		}

		int amplifier() {
			return amplifier;
		}
	}

	private final ActiveEvent owner;
	private final List<Entry> entries = new ArrayList<>();

	public TrackedEffects(ActiveEvent owner) {
		this.owner = owner;
	}

	/**
	 * Gives {@code effect} at {@code amplifier} (0 = level I) until the event ends. A second call for the same entity
	 * and effect replaces the amplifier. Returns false if the entity refused it (e.g. immune) or is a player this instance may not change
	 * ({@link ActiveEvent#mayChange}: Creative / Spectator, dead, other dimension).
	 */
	public boolean give(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		if (!owner.mayChange(entity)) return false;
		entries.removeIf(e -> e.entity() == entity && e.effect().equals(effect));
		Entry entry = new Entry(entity, effect, Math.max(0, amplifier));
		entries.add(entry);
		return apply(entry);
	}

	private boolean apply(Entry e) {
		int duration = owner.remainingTicks() + ChaosLimits.EFFECT_MARGIN_TICKS;
		e.applied = Math.max(e.applied, duration);
		return e.entity().addEffect(new MobEffectInstance(e.effect(), duration, e.amplifier(), false, true, true));
	}

	public boolean tracks(Entity entity) {
		for (Entry e : entries) if (e.entity() == entity) return true;
		return false;
	}

	/** Entities that currently carry an effect of this instance. */
	public List<LivingEntity> entities() {
		List<LivingEntity> out = new ArrayList<>();
		for (Entry e : entries) if (!out.contains(e.entity())) out.add(e.entity());
		return out;
	}

	/**
	 * Framework, every EFFECT_REFRESH_TICKS: re-apply missing / weaker / too short effects; players this instance may
	 * no longer change (switched to Creative, left the dimension) are reverted instead.
	 */
	public void refresh() {
		for (LivingEntity entity : entities()) if (!owner.mayChange(entity)) revert(entity);
		int need = owner.remainingTicks() + ChaosLimits.EFFECT_MARGIN_TICKS;
		for (Entry e : entries) {
			if (e.entity().isRemoved() || !e.entity().isAlive()) continue;
			MobEffectInstance current = e.entity().getEffect(e.effect());
			if (current == null || current.getAmplifier() < e.amplifier()
					|| (current.getAmplifier() == e.amplifier() && !current.isInfiniteDuration()
					&& current.getDuration() + 2 * ChaosLimits.EFFECT_REFRESH_TICKS < need)) {
				apply(e);
			}
		}
	}

	/** Framework: removes this instance's effects from {@code entity}. */
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

	/** Framework: the entity unloaded; its effect runs out by itself. */
	public void forget(Entity entity) {
		entries.removeIf(e -> e.entity() == entity);
	}

	private void remove(Entry e) {
		LivingEntity entity = e.entity();
		MobEffectInstance top = entity.getEffect(e.effect());
		if (top == null) return;
		if (isOurs(e, top)) {
			// vanilla drops the whole stack: take ours away, then put back what was hidden under it (a potion, a
			// beacon) with its remaining time
			MobEffectInstance hidden = strip(e, hidden(top));
			entity.removeEffect(e.effect());
			if (hidden != null) entity.addEffect(hidden);
			return;
		}
		// a stronger or longer foreign effect is on top (left alone); ours may be hidden under it: unlink it
		setHidden(top, strip(e, hidden(top)));
	}

	/**
	 * Ours: the amplifier we gave and never longer than anything we gave. A longer one (beacon, potion drunk during
	 * the event) or an infinite one is foreign.
	 */
	private static boolean isOurs(Entry e, MobEffectInstance instance) {
		return !instance.isInfiniteDuration() && instance.getAmplifier() == e.amplifier() && instance.getDuration() <= e.applied;
	}

	/** The hidden chain starting at {@code head} without this entry's instances. */
	private static @Nullable MobEffectInstance strip(Entry e, @Nullable MobEffectInstance head) {
		while (head != null && isOurs(e, head)) head = hidden(head);
		MobEffectInstance node = head;
		while (node != null) {
			MobEffectInstance next = hidden(node);
			while (next != null && isOurs(e, next)) next = hidden(next);
			setHidden(node, next);
			node = next;
		}
		return head;
	}

	private static @Nullable MobEffectInstance hidden(MobEffectInstance instance) {
		return ((MobEffectInstanceAccessor) instance).chaosaholic$getHiddenEffect();
	}

	private static void setHidden(MobEffectInstance instance, @Nullable MobEffectInstance hidden) {
		((MobEffectInstanceAccessor) instance).chaosaholic$setHiddenEffect(hidden);
	}
}
