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
 * Good: Regeneration II for 20-40 s, with a heart particle every {@link #HEART_PERIOD} ticks. The effect goes through
 * the tracker (re-applied after milk, removed at the end unless a longer foreign Regeneration replaced it).
 */
public final class HealingAura extends ChaosEvent {
	/** Amplifier 1 = Regeneration II. */
	public static final int AMPLIFIER = 1;
	/** Ongoing heart particle, every 2 s. */
	public static final int HEART_PERIOD = 40;

	public HealingAura() {
		super("healing_aura", Category.GOOD, 20, 40);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.effects().give(player, MobEffects.REGENERATION, AMPLIFIER);
		ev.level().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 6, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public void onTick(ActiveEvent ev) {
		if (ev.age() == 0 || !ev.every(HEART_PERIOD)) return; // the start already showed hearts
		for (ServerPlayer p : ev.players()) {
			ev.level().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.1, p.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_POWER_SELECT);
	}

	@Override
	public float startSoundPitch() {
		return 1.4F;
	}

	@Override
	public float startSoundVolume() {
		return 0.6F;
	}
}
