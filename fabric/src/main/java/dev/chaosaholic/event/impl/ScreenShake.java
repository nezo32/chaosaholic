package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Sounds;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Weird: the camera wobbles for 10-15 s. The wobble itself is client-side (GameRendererMixin + core.ShakeCurve:
 * ≤ 1.5°, ~8 Hz, eased in over 0.5 s and out over the last 1 s) and reads the synced active-events list, so it
 * follows this instance's timer, extensions (smoothly: the phase runs on a client-side elapsed counter) and every
 * removal path without server code. The angles are scaled by the vanilla Screen Effects accessibility slider. Players
 * who switched Screen effects off (Chaosaholic setting or the slider at 0 %), and players without the mod, only get
 * the boss bar, the sound and the particles. Nothing to clean up on the server.
 */
public final class ScreenShake extends ChaosEvent {
	public static final String ID = "screen_shake";

	public ScreenShake() {
		super("screen_shake", Category.WEIRD, 10, 15);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		Sounds.play(player, SoundEvents.WIND_CHARGE_BURST, 0.4F, 0.6F);
		ev.level().sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.5, 0.05, 0.5, 0.02);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return SoundEvents.NOTE_BLOCK_BASS;
	}

	@Override
	public float startSoundPitch() {
		return 0.5F;
	}
}
