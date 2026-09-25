package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;

/**
 * Bad: Darkness (not Blindness: pulsing, less nauseating, presentation.md §5) for 5-10 s through the effect tracker.
 * Not a hazard, so no warning. Extensions never push the remaining time (nor the boss bar's total) past
 * {@link #MAX_TICKS} ({@link #maxRemainingTicks}), so a blackout stays short however often it is rolled.
 */
public final class Blackout extends ChaosEvent {
	/** Longest remaining time, also after extensions (10 s). */
	public static final int MAX_TICKS = 10 * 20;

	public Blackout() {
		super("blackout", Category.BAD, 5, 10);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.DARKNESS, 0);
		ev.level().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public int maxRemainingTicks() {
		return MAX_TICKS;
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT);
	}

	@Override
	public float startSoundVolume() {
		return 0.6F;
	}
}
