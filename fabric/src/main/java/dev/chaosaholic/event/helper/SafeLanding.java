package dev.chaosaholic.event.helper;

import dev.chaosaholic.event.RemoveReason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Soft landing for players who leave a movement event (moon_jump, feather_fall, bouncy_floor, gravity_flip) while in
 * the air: without it, a fall that the event made harmless (or a bounce / jump it made high) would land with full
 * fall damage once the event is gone, a Hardcore death. Call it from {@code onPlayerRemoved} for every reason: the
 * player gets {@link #TAIL_TICKS} of plain Slow Falling (which also resets the fall distance), not through the
 * tracker, so the framework's revert never takes it away and it is saved with the player on logout (LEAVE runs before
 * the save). Nothing happens on death or on the ground.
 *
 * <p>"In the air" is any player off the ground outside water, lava and vehicles, including one still rising: the
 * server cannot tell a harmless hop from a high bounce reliably (players move client-side), and an extra few seconds
 * of Slow Falling never hurt.
 */
public final class SafeLanding {
	/** Slow Falling tail, 10 s: enough to come down softly from any height an event can reach. */
	public static final int TAIL_TICKS = 200;

	private SafeLanding() {}

	/**
	 * Gives {@code player} the Slow Falling tail if they are airborne and {@code reason} is not
	 * {@link RemoveReason#DEATH}. A longer (or infinite) Slow Falling they already have is kept. Returns true if the
	 * tail was given.
	 */
	public static boolean give(ServerPlayer player, RemoveReason reason) {
		if (reason == RemoveReason.DEATH || !isAirborne(player)) return false;
		MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
		if (current != null && (current.isInfiniteDuration() || current.getDuration() >= TAIL_TICKS)) return false;
		return player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, TAIL_TICKS, 0, false, true, true));
	}

	/** Alive, off the ground, not swimming in water / lava and not riding anything. */
	public static boolean isAirborne(ServerPlayer player) {
		return player.isAlive() && !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger();
	}
}
