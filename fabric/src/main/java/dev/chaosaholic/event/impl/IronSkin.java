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

/** Good: Resistance II (-40 % damage taken) for 30-60 s through the effect tracker. */
public final class IronSkin extends ChaosEvent {
	/** Amplifier 1 = Resistance II. */
	public static final int AMPLIFIER = 1;

	public IronSkin() {
		super("iron_skin", Category.GOOD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.RESISTANCE, AMPLIFIER);
		ev.level().sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.ARMOR_EQUIP_IRON;
	}

	@Override
	public float startSoundPitch() {
		return 0.8F;
	}
}
