package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.EventManager;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Good: for 60-120 s, experience orbs picked up by an affected player give twice their points. The hook is
 * {@code ExperienceOrbMixin} ({@code ExperienceOrb#playerTouch} → {@link #points}): the doubled amount is what is left
 * after Mending repaired items, so Mending is not doubled, and only orbs count (not /xp, advancements or other
 * direct grants). Levels gained from the doubled points trigger new events like any other level-up (by design; the
 * framework's queue cap still applies). Nothing to clean up: the check is live, so it stops the tick the player
 * leaves the event.
 */
public final class DoubleXp extends ChaosEvent {
	public static final String ID = "double_xp";
	public static final int MULTIPLIER = 2;

	public DoubleXp() {
		super("double_xp", Category.GOOD, 60, 120);
	}

	/** Points {@code player} really receives from an orb worth {@code points} (after Mending). Server thread. */
	public static int points(Player player, int points) {
		if (points <= 0 || !(player instanceof ServerPlayer sp) || !EventManager.isEligible(sp) || !EventManager.isActiveFor(sp, ID)) {
			return points;
		}
		return points > Integer.MAX_VALUE / MULTIPLIER ? Integer.MAX_VALUE : points * MULTIPLIER;
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP);
	}

	@Override
	public float startSoundPitch() {
		return 0.7F;
	}
}
