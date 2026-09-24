package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;

/**
 * Bad: Slowness II and Mining Fatigue I for 20-40 s through the effect tracker. Not a hazard (nothing falls or
 * spawns), so no warning; both effects are removed at the end unless a longer foreign one replaced them.
 */
public final class Sluggish extends ChaosEvent {
	/** Amplifier 1 = Slowness II. */
	public static final int SLOWNESS_AMPLIFIER = 1;
	/** Amplifier 0 = Mining Fatigue I. */
	public static final int FATIGUE_AMPLIFIER = 0;

	public Sluggish() {
		super("sluggish", Category.BAD, 20, 40);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.SLOWNESS, SLOWNESS_AMPLIFIER);
		ev.effects().give(player, MobEffects.MINING_FATIGUE, FATIGUE_AMPLIFIER);
		ev.level().sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1, player.getZ(), 20, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.NOTE_BLOCK_DIDGERIDOO;
	}

	@Override
	public float startSoundPitch() {
		return 0.5F;
	}
}
