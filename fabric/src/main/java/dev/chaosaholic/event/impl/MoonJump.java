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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Good: Jump Boost III and no fall damage for 30-60 s. The effect goes through the tracker; fall damage of affected
 * players is cancelled through the routed ALLOW_DAMAGE hook (like FeatherFall), so there is nothing else to clean up.
 * A jump still in the air when the event ends lands without the boost: about 3.3 blocks at most, i.e. half a heart.
 */
public final class MoonJump extends ChaosEvent {
	/** Amplifier 2 = Jump Boost III. */
	public static final int AMPLIFIER = 2;

	public MoonJump() {
		super("moon_jump", Category.GOOD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.JUMP_BOOST, AMPLIFIER);
		ev.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5, player.getZ(), 10, 0.5, 0.3, 0.5, 0.05);
	}

	@Override
	public boolean allowDamage(ActiveEvent ev, LivingEntity entity, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && ev.isAffected(entity));
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SLIME_JUMP);
	}

	@Override
	public float startSoundPitch() {
		return 0.7F;
	}
}
