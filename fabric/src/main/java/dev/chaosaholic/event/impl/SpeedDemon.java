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

/** Good: Speed III + Haste II for 30-60 s. The reference "effects only" event: the tracker does all the cleanup. */
public final class SpeedDemon extends ChaosEvent {
	public SpeedDemon() {
		super("speed_demon", Category.GOOD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.SPEED, 2); // amplifier 2 = level III
		ev.effects().give(player, MobEffects.HASTE, 1);
		ev.level().sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BREEZE_JUMP);
	}

	@Override
	public float startSoundPitch() {
		return 1.3F;
	}
}
