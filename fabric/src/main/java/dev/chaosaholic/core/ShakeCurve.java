package dev.chaosaholic.core;

/**
 * Camera wobble of the screen_shake event (client side, design/presentation.md §5): smooth sine oscillation of about
 * {@link #FREQUENCY_HZ} Hz, at most {@link #MAX_DEGREES} degrees on every axis, eased in over {@link #EASE_IN_TICKS}
 * and out over the last {@link #EASE_OUT_TICKS}. Pure math: the client feeds it the synced timer, the renderer
 * applies the angles to the camera pose (never to the player's real rotation). No flashing: only rotation, and
 * continuous in time.
 */
public final class ShakeCurve {
	/** Hard cap of every angle, in degrees. */
	public static final float MAX_DEGREES = 1.5F;
	/** Main wobble frequency. */
	public static final double FREQUENCY_HZ = 8.0;
	/** Fade-in at the start (0.5 s). */
	public static final int EASE_IN_TICKS = 10;
	/** Fade-out before the end (1 s). */
	public static final int EASE_OUT_TICKS = 20;

	/** Rotation of the camera pose, in degrees: pitch (x), yaw (y), roll (z). */
	public record Angles(float pitch, float yaw, float roll) {
		public static final Angles NONE = new Angles(0.0F, 0.0F, 0.0F);

		public boolean isNone() {
			return pitch == 0.0F && yaw == 0.0F && roll == 0.0F;
		}
	}

	private ShakeCurve() {}

	/**
	 * Strength 0..1: smoothstep in over the first {@link #EASE_IN_TICKS}, smoothstep out over the last
	 * {@link #EASE_OUT_TICKS}; 0 for invalid input (not started, over, NaN).
	 */
	public static float envelope(float elapsedTicks, float remainingTicks) {
		if (!(elapsedTicks > 0.0F) || !(remainingTicks > 0.0F)) return 0.0F; // also catches NaN
		return Math.min(smooth(elapsedTicks / EASE_IN_TICKS), smooth(remainingTicks / EASE_OUT_TICKS));
	}

	/**
	 * Angles at a moment of the run. {@code elapsedTicks} = ticks since the start (with the partial tick),
	 * {@code remainingTicks} = ticks until the end (minus the partial tick). Every angle stays within
	 * ±{@link #MAX_DEGREES}.
	 */
	public static Angles angles(float elapsedTicks, float remainingTicks) {
		float strength = envelope(elapsedTicks, remainingTicks);
		if (strength <= 0.0F) return Angles.NONE;
		double t = elapsedTicks / ChaosLimits.TICKS_PER_SECOND; // seconds
		double w = 2.0 * Math.PI * FREQUENCY_HZ;
		double a = MAX_DEGREES * strength;
		// weights of each axis sum to at most 1, so |angle| <= a <= MAX_DEGREES
		double yaw = a * (0.65 * Math.sin(w * t) + 0.35 * Math.sin(w * 0.66 * t + 1.3));
		double pitch = a * 0.6 * Math.sin(w * 0.84 * t + 0.7);
		double roll = a * 0.8 * Math.sin(w * 0.97 * t + 2.1);
		return new Angles(clamp(pitch), clamp(yaw), clamp(roll));
	}

	private static float smooth(float x) {
		if (x <= 0.0F) return 0.0F;
		if (x >= 1.0F) return 1.0F;
		return x * x * (3.0F - 2.0F * x);
	}

	private static float clamp(double degrees) {
		return (float) Math.max(-MAX_DEGREES, Math.min(MAX_DEGREES, degrees));
	}
}
