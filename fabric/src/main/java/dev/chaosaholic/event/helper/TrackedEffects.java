package dev.chaosaholic.event.helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.event.ActiveEvent;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mob effects an event keeps on entities for its whole run. Given with the instance's remaining time (plus a small
 * margin), so even a crash leaves at most that much behind; re-applied every {@link ChaosLimits#EFFECT_REFRESH_TICKS}
 * if removed (milk) or after an extension; removed when the entity stops being affected, unless something else
 * replaced it with a stronger or longer effect in the meantime.
 */
public final class TrackedEffects {
	private record Entry(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {}

	private final ActiveEvent owner;
	private final List<Entry> entries = new ArrayList<>();

	public TrackedEffects(ActiveEvent owner) {
		this.owner = owner;
	}

	/**
	 * Gives {@code effect} at {@code amplifier} (0 = level I) until the event ends. A second call for the same entity
	 * and effect replaces the amplifier. Returns false if the entity refused it (e.g. immune).
	 */
	public boolean give(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		entries.removeIf(e -> e.entity() == entity && e.effect().equals(effect));
		Entry entry = new Entry(entity, effect, Math.max(0, amplifier));
		entries.add(entry);
		return apply(entry);
	}

	private boolean apply(Entry e) {
		int duration = owner.remainingTicks() + ChaosLimits.EFFECT_MARGIN_TICKS;
		return e.entity().addEffect(new MobEffectInstance(e.effect(), duration, e.amplifier(), false, true, true));
	}

	public boolean tracks(Entity entity) {
		for (Entry e : entries) if (e.entity() == entity) return true;
		return false;
	}

	/** Framework, every EFFECT_REFRESH_TICKS: re-apply missing / weaker / too short effects. */
	public void refresh() {
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
		MobEffectInstance current = e.entity().getEffect(e.effect());
		if (current == null || current.isInfiniteDuration() || current.getAmplifier() != e.amplifier()) return;
		// ours or shorter; a longer one (beacon, potion drunk during the event) is left alone
		if (current.getDuration() <= owner.remainingTicks() + ChaosLimits.EFFECT_MARGIN_TICKS + 2 * ChaosLimits.EFFECT_REFRESH_TICKS) {
			e.entity().removeEffect(e.effect());
		}
	}
}
