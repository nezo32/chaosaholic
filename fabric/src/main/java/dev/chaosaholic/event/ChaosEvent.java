package dev.chaosaholic.event;

import dev.chaosaholic.core.ChaosLimits;
import dev.chaosaholic.core.EventIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One chaos event type. Adding an event = one subclass (in {@code event.impl}) + one line in {@link ChaosEvents} +
 * the lang keys {@code chaosaholic.event.<id>}, {@code .desc}, {@code .announce} (and {@code .warning} when
 * {@link #hasWarning()}). See ARCHITECTURE.md, "Event authoring guide".
 *
 * <p>The object is a stateless singleton: everything that belongs to one run lives on the {@link ActiveEvent}
 * ({@link ActiveEvent#state}, its trackers and schedule). All hooks run on the server thread; an exception in a hook
 * is logged and ends that instance with {@link StopReason#ERROR} (cleanup still runs).
 *
 * <p>Lifecycle of an instance: {@link #canStart} → {@link #onStart} → {@link #onPlayerAdded} per player →
 * {@link #onTick} every tick (and {@link #onExtended} when rolled again) → {@link #onPlayerRemoved} per player →
 * {@link #onStop}. Instant events run start + players + remove + stop in the same tick and get no boss bar.
 */
public abstract class ChaosEvent {
	private final String id;
	private final Category category;
	private final int minTicks;
	private final int maxTicks;
	private final int defaultWeight;

	/**
	 * @param minSeconds shortest run; 0 together with maxSeconds 0 = instant event
	 * @param maxSeconds longest run (the duration is rolled uniformly in between)
	 */
	protected ChaosEvent(String id, Category category, int minSeconds, int maxSeconds) {
		this(id, category, minSeconds, maxSeconds, ChaosLimits.DEFAULT_WEIGHT);
	}

	protected ChaosEvent(String id, Category category, int minSeconds, int maxSeconds, int defaultWeight) {
		if (!EventIds.isValid(id)) throw new IllegalArgumentException("invalid event id: " + id);
		if (minSeconds < 0 || maxSeconds < minSeconds) throw new IllegalArgumentException(id + ": bad duration " + minSeconds + ".." + maxSeconds);
		if (maxSeconds * ChaosLimits.TICKS_PER_SECOND > ChaosLimits.MAX_REMAINING_TICKS) {
			throw new IllegalArgumentException(id + ": duration above the cap " + ChaosLimits.MAX_REMAINING_TICKS / 20 + " s");
		}
		this.id = id;
		this.category = category;
		this.minTicks = minSeconds * ChaosLimits.TICKS_PER_SECOND;
		this.maxTicks = maxSeconds * ChaosLimits.TICKS_PER_SECOND;
		this.defaultWeight = ChaosLimits.clampWeight(defaultWeight);
	}

	/** Instant event: {@code super(id, category, INSTANT, INSTANT)}. */
	protected static final int INSTANT = 0;

	public final String id() {
		return id;
	}

	public final Category category() {
		return category;
	}

	public final int defaultWeight() {
		return defaultWeight;
	}

	public final boolean isInstant() {
		return maxTicks == 0;
	}

	public final int minTicks() {
		return minTicks;
	}

	public final int maxTicks() {
		return maxTicks;
	}

	/** Duration of a new run (or of an extension), in ticks. Override for difficulty-dependent durations. */
	public int rollDuration(EventContext ctx) {
		if (maxTicks <= minTicks) return minTicks;
		return minTicks + ctx.random().nextInt(maxTicks - minTicks + 1);
	}

	/**
	 * Longest remaining time of a run, in ticks, also after extensions (stacking): an extension never pushes the timer
	 * (nor the boss bar's total) above it. Default: the framework cap {@link ChaosLimits#MAX_REMAINING_TICKS}; a
	 * larger value is ignored. Override for events that must stay short however often they are rolled.
	 */
	public int maxRemainingTicks() {
		return ChaosLimits.MAX_REMAINING_TICKS;
	}

	/**
	 * Whether the event can start now for these players. False = it is not rolled (another event is rolled instead)
	 * and /chaosaholic trigger reports "no valid target". Examples: no safe spot, Peaceful for a mob event.
	 */
	public boolean canStart(EventContext ctx) {
		return true;
	}

	/** Once, before any player is added: world-level setup (spawn entities, change time, ...). */
	public void onStart(ActiveEvent ev) {}

	/** Per affected player, right after onStart: per-player effects. Also the place for start particles. */
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {}

	/** Every server tick while active (not for instant events). {@link ActiveEvent#players()} is already filtered. */
	public void onTick(ActiveEvent ev) {}

	/** The same event was rolled again for (some of) the same players; the timer has already been extended. */
	public void onExtended(ActiveEvent ev, int addedTicks) {}

	/**
	 * A player stops being affected (event end, logout, death, dimension change, Creative/Spectator). Revert every
	 * per-player change made without the trackers. The player object may already be removed from the level.
	 */
	public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {}

	/** Once, after every player was removed: revert world-level changes not made through the trackers. */
	public void onStop(ActiveEvent ev, StopReason reason) {}

	// Routed hooks: the framework registers one Fabric listener each and calls them on every active instance.
	// Check ev.isAffected(...) / ev.tracks(...) yourself.

	/** ServerLivingEntityEvents.ALLOW_DAMAGE. Return false to cancel the damage. */
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return true;
	}

	/** PlayerBlockBreakEvents.AFTER, for any player. */
	public void afterBlockBreak(ActiveEvent ev, ServerPlayer player, BlockPos pos, BlockState state) {}

	/** ServerLivingEntityEvents.AFTER_DEATH of a non-player entity killed by a player (source.getEntity()). */
	public void afterKill(ActiveEvent ev, ServerPlayer killer, LivingEntity victim, DamageSource source) {}

	/**
	 * ServerLivingEntityEvents.MOB_CONVERSION of a mob this instance owns (zombie → drowned, skeleton → stray, ...):
	 * {@code converted} is already owned by the instance (it inherited the mark) but not yet in the level. Re-apply
	 * per-mob settings that the conversion does not copy (loot, targets, ...).
	 */
	public void onOwnedConverted(ActiveEvent ev, Mob previous, Mob converted) {}

	/** True for events that call helper.Warning: the lang file must then have {@code chaosaholic.event.<id>.warning}. */
	public boolean hasWarning() {
		return false;
	}

	/** Event-specific start sound played with the announcement (design/presentation.md §3); null = category sting only. */
	public @Nullable Holder<SoundEvent> startSound() {
		return null;
	}

	public float startSoundPitch() {
		return 1.0F;
	}

	public float startSoundVolume() {
		return 0.8F;
	}

	@Override
	public String toString() {
		return "ChaosEvent[" + id + "]";
	}
}
