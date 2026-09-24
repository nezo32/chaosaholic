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
 * Weird: the player and the mobs around them (radius {@link #RADIUS}, capped by Area) shrink to half size for
 * 30-60 s. The reference "area + attribute modifier" event: transient SCALE modifiers through the tracker, reverted
 * per player on logout / death / Creative, for everything at the end, and never saved (chunk unload, crash).
 */
public final class TinyWorld extends ChaosEvent {
	public static final double RADIUS = 12.0;
	/** ADD_MULTIPLIED_TOTAL -0.5 = half size. */
	public static final double SCALE = -0.5;

	public TinyWorld() {
		super("tiny_world", Category.WEIRD, 30, 60);
	}

	@Override
	public void onPlayerAdded(ActiveEvent ev, ServerPlayer player) {
		ev.modifiers().add(player, Attributes.SCALE, SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		for (LivingEntity mob : Area.mobs(ev.level(), player.position(), RADIUS)) {
			if (ev.modifiers().tracks(mob)) continue; // world scope: shared neighbours shrink once
			ev.modifiers().add(mob, Attributes.SCALE, SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		ev.level().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.6, 0.6, 0.6, 0.05);
	}

	@Override
	public Holder<SoundEvent> startSound() {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PUFFER_FISH_BLOW_UP);
	}

	@Override
	public float startSoundPitch() {
		return 1.6F;
	}
}
