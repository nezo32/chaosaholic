package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Good: no fall damage for 60-120 s. The reference "routed hook" event: the framework's single ALLOW_DAMAGE listener
 * asks every running instance; this one cancels fall damage of its affected players. Nothing to clean up.
 */
public final class FeatherFall extends ChaosEvent {
	public FeatherFall() {
		super("feather_fall", Category.GOOD, 60, 120);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.4, 0.1, 0.4, 0.02);
	}

	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && ev.isAffected(entity));
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.NOTE_BLOCK_CHIME;
	}

	@Override
	public float startSoundPitch() {
		return 1.6F;
	}
}
