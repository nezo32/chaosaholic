package dev.chaosaholic.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The weaker, longer effect vanilla keeps "hidden" under a stronger one (e.g. a Swiftness I potion under an event's
 * Speed III). TrackedEffects needs it to take only its own layer away when an event ends.
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
	@Accessor("hiddenEffect")
	@Nullable MobEffectInstance chaosaholic$getHiddenEffect();

	@Accessor("hiddenEffect")
	void chaosaholic$setHiddenEffect(@Nullable MobEffectInstance hidden);
}
