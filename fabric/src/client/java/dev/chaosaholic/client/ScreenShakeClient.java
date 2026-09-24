package dev.chaosaholic.client;

import dev.chaosaholic.core.ShakeCurve;
import dev.chaosaholic.event.impl.ScreenShake;
import net.minecraft.client.Minecraft;

/**
 * Client side of screen_shake: the camera wobble angles for the current frame, from the synced event timer
 * ({@link ClientEventState}) and {@link ShakeCurve}. {@link ShakeCurve.Angles#NONE} when the event is not active for
 * the local player or the player switched Screen effects off. The phase follows the locally counted elapsed ticks,
 * so an extension (remaining time jumps up) does not make the camera jump. The angles are scaled by the vanilla
 * accessibility option "Screen Effects" ({@code screenEffectScale}, 0-100 %, the one that also tones down nausea
 * and portal wobble). Used by the GameRenderer mixin.
 */
public final class ScreenShakeClient {
	private ScreenShakeClient() {}

	public static ShakeCurve.Angles angles() {
		if (!ClientEventState.isActiveWithScreenEffects(ScreenShake.ID)) return ShakeCurve.Angles.NONE;
		int remaining = ClientEventState.remainingTicks(ScreenShake.ID);
		int elapsed = ClientEventState.elapsedTicks(ScreenShake.ID);
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.isPaused() ? 0.0F : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		ShakeCurve.Angles a = ShakeCurve.angles(elapsed + partial, remaining - partial);
		return ShakeCurve.scale(a, mc.options.screenEffectScale().get().floatValue());
	}
}
