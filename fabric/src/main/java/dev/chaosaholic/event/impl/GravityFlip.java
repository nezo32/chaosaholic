package dev.chaosaholic.event.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.RemoveReason;
import dev.chaosaholic.event.helper.SafeLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Weird: for 20-30 s the player floats up (Levitation II, {@link #UP_TICKS}) and drifts down ({@link #DOWN_TICKS})
 * by turns, starting with "up". Slow Falling is kept for the whole run (tracked): Levitation overrides it while
 * rising, and it takes over as soon as a Levitation phase ends, so every descent is soft.
 *
 * <p>Safety (Hardcore included):
 * <ul>
 *   <li>height cap: a player more than {@link #MAX_RISE} blocks above the ground (no solid block or fluid below)
 *       drifts down for the rest of that up phase (checked every {@link #CAP_CHECK_TICKS} ticks);</li>
 *   <li>the last {@link #FINAL_DOWN_TICKS} are always a down phase (a flip up happens only with enough time left);</li>
 *   <li>when a player stops being affected (end, stop, logout, dimension change, Creative) while in the air, they
 *       get the {@link SafeLanding} tail of plain Slow Falling to land softly; it is saved with the player on logout;</li>
 *   <li>Levitation is given per phase (never longer than the phase + {@link #MARGIN_TICKS}) and not through the
 *       tracker, so a crash leaves at most a few seconds of it, always together with the longer Slow Falling.</li>
 * </ul>
 */
public final class GravityFlip extends ChaosEvent {
	/** One up phase (Levitation II rises about 2 blocks per second). */
	public static final int UP_TICKS = 60;
	/** One down phase. */
	public static final int DOWN_TICKS = 80;
	/** The end of every run is a down phase of at least this long. */
	public static final int FINAL_DOWN_TICKS = 100;
	/** Levitation amplifier (1 = level II). */
	public static final int LEVITATION_AMPLIFIER = 1;
	/** Max height above the ground during an up phase, in blocks. */
	public static final int MAX_RISE = 6;
	/** How often (ticks) rising players are checked against {@link #MAX_RISE} during an up phase. */
	public static final int CAP_CHECK_TICKS = 5;
	/** Extra Levitation time beyond a phase, so the effect never flickers off before the flip. */
	public static final int MARGIN_TICKS = 10;

	private static final class State {
		boolean up = true;
		int nextFlip = UP_TICKS;
		/** Players over the height cap in the current up phase: they drift down until the next up phase. */
		final Set<UUID> capped = new HashSet<>();
		/** Phase each player currently has (true = up). */
		final Map<UUID, Boolean> phase = new HashMap<>();
	}

	public GravityFlip() {
		super("gravity_flip", Category.WEIRD, 20, 30);
	}

	@Override
	public void onStart(ActiveEvent ev) {
		ev.state(State::new);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		State s = ev.state(State::new);
		if (s.up && tooHigh(ev.level(), player.blockPosition())) s.capped.add(player.getUUID());
		apply(ev, s, player);
		ev.level().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		State s = ev.state(State::new);
		boolean mustLand = s.up && ev.remainingTicks() <= FINAL_DOWN_TICKS;
		if (ev.age() >= s.nextFlip || mustLand) {
			if (s.up) {
				s.up = false;
				s.nextFlip = ev.age() + DOWN_TICKS;
			} else if (ev.remainingTicks() > UP_TICKS + FINAL_DOWN_TICKS) {
				s.up = true;
				s.nextFlip = ev.age() + UP_TICKS;
				s.capped.clear();
			} else {
				s.nextFlip = ev.age() + DOWN_TICKS; // stay down; an extension may allow another flip later
			}
			for (ServerPlayer p : ev.players()) {
				if (s.up && tooHigh(ev.level(), p.blockPosition())) s.capped.add(p.getUUID());
				if (apply(ev, s, p)) {
					ev.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
				}
			}
		} else if (s.up && ev.every(CAP_CHECK_TICKS)) {
			for (ServerPlayer p : ev.players()) {
				if (s.capped.contains(p.getUUID()) || !tooHigh(ev.level(), p.blockPosition())) continue;
				s.capped.add(p.getUUID());
				apply(ev, s, p);
			}
		}
	}

	/** Gives {@code player} the effects of the current phase. Returns true if the phase changed for them. */
	private static boolean apply(ActiveEvent ev, State s, ServerPlayer player) {
		boolean up = s.up && !s.capped.contains(player.getUUID());
		Boolean before = s.phase.put(player.getUUID(), up);
		if (before == null) ev.effects().give(player, MobEffects.SLOW_FALLING, 0); // whole run
		else if (before == up) return false;
		if (up) {
			int ticks = Math.max(1, s.nextFlip - ev.age()) + MARGIN_TICKS;
			player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, LEVITATION_AMPLIFIER, false, true, true));
		} else {
			removeLevitation(player);
		}
		return true;
	}

	@Override
	public void onPlayerRemoved(ActiveEvent ev, ServerPlayer player, RemoveReason reason) {
		ev.state(State::new).phase.remove(player.getUUID());
		removeLevitation(player);
		ev.effects().revert(player); // before the tail, so the framework's revert cannot take the tail away
		SafeLanding.give(player, reason);
	}

	/** Removes Levitation only if it is ours: our amplifier and never longer than one phase. */
	private static void removeLevitation(ServerPlayer player) {
		MobEffectInstance current = player.getEffect(MobEffects.LEVITATION);
		if (current == null || current.isInfiniteDuration() || current.getAmplifier() != LEVITATION_AMPLIFIER) return;
		if (current.getDuration() <= Math.max(UP_TICKS, DOWN_TICKS) + MARGIN_TICKS) player.removeEffect(MobEffects.LEVITATION);
	}

	/** True if nothing solid (no collision box, no fluid) is within {@link #MAX_RISE} blocks below {@code feet}. */
	public static boolean tooHigh(ServerLevel level, BlockPos feet) {
		for (int i = 0; i <= MAX_RISE; i++) {
			BlockPos pos = feet.below(i);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
		}
		return true;
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SHULKER_SHOOT);
	}

	@Override
	public float startSoundPitch() {
		return 1.2F;
	}
}
