package dev.chaosaholic.event.impl;

import dev.chaosaholic.event.ActiveEvent;
import dev.chaosaholic.event.Category;
import dev.chaosaholic.event.ChaosEvent;
import dev.chaosaholic.event.helper.Area;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Weird: for 30-45 s every block feels like ice for the player and up to {@link #MAX_MOBS} mobs within
 * {@link #RADIUS} blocks (fair game only). A transient FRICTION_MODIFIER modifier of {@link #FRICTION}
 * (ADD_MULTIPLIED_TOTAL: 1.0 becomes 0.05, so stone 0.6 slides like ice 0.98). The attribute is synced, so the
 * client, even a vanilla one, predicts the sliding itself. Reverted per player on removal and for all at the end;
 * never saved (unload / crash leave nothing).
 */
public final class Slippery extends ChaosEvent {
	public static final double RADIUS = 8.0;
	/** Mobs that slide along with each player (the nearest ones). */
	public static final int MAX_MOBS = 16;
	/** ADD_MULTIPLIED_TOTAL -0.95: friction modifier 0.05 = ice everywhere. */
	public static final double FRICTION = -0.95;

	public Slippery() {
		super("slippery", Category.WEIRD, 30, 45);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		slide(ev, player);
		int mobs = 0;
		for (LivingEntity mob : Area.mobs(ev.level(), player.position(), RADIUS)) {
			if (mobs >= MAX_MOBS) break;
			if (ev.modifiers().tracks(mob)) continue; // world scope: shared neighbours once
			if (slide(ev, mob)) mobs++;
		}
		ev.level().sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.6, 0.1, 0.6, 0.02);
	}

	private static boolean slide(ActiveEvent ev, LivingEntity entity) {
		return ev.modifiers().add(entity, Attributes.FRICTION_MODIFIER, FRICTION, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.POWDER_SNOW_STEP);
	}

	@Override
	public float startSoundPitch() {
		return 1.4F;
	}
}
